import { useMutation, useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useApiClient } from '../api/context';
import { avviaCaricamento, chiavi, leggiLotto, type RichiestaCaricamento } from '../api/queries';
import type { Caricamento, LottoCaricamento } from '../api/types';

export const INTERVALLO_POLLING_MS = 1500;

export interface StatoCaricamentoLotto {
  /** Lotto in corso o concluso (null prima dell'avvio). */
  lotto: LottoCaricamento | null;
  inviando: boolean;
  erroreInvio: unknown;
  errorePolling: unknown;
  inElaborazione: boolean;
  avvia: (richiesta: RichiestaCaricamento) => void;
  /** Torna alla selezione dei file per un nuovo lotto. */
  azzera: () => void;
}

/**
 * Invio di un lotto di PDF e polling del suo stato finche' tutti i file sono in uno stato terminale.
 * Vive nel componente radice cosi' il caricamento prosegue anche a modale chiusa.
 */
export function useCaricamento(
  onCompletato: (lotto: LottoCaricamento) => void,
  intervalloMs: number = INTERVALLO_POLLING_MS,
): StatoCaricamentoLotto {
  const api = useApiClient();
  const [idLotto, setIdLotto] = useState<string | null>(null);
  const notificati = useRef(new Set<string>());
  const callback = useRef(onCompletato);

  useEffect(() => {
    callback.current = onCompletato;
  }, [onCompletato]);

  const invio = useMutation({
    mutationFn: (richiesta: RichiestaCaricamento) => avviaCaricamento(api, richiesta),
    onSuccess: (lotto) => setIdLotto(lotto.lotto),
  });

  const stato = useQuery({
    queryKey: chiavi.lotto(idLotto ?? ''),
    queryFn: ({ signal }) => leggiLotto(api, idLotto ?? '', signal),
    enabled: idLotto !== null,
    initialData: () => (invio.data?.lotto === idLotto ? invio.data : undefined),
    initialDataUpdatedAt: 0,
    refetchInterval: (query) => (query.state.data?.completato ? false : intervalloMs),
    refetchIntervalInBackground: true,
    retry: 3,
  });

  const lotto = idLotto !== null ? (stato.data ?? null) : null;

  useEffect(() => {
    if (lotto?.completato && !notificati.current.has(lotto.lotto)) {
      notificati.current.add(lotto.lotto);
      callback.current(lotto);
    }
  }, [lotto]);

  const { mutate, reset } = invio;
  const avvia = useCallback((richiesta: RichiestaCaricamento) => mutate(richiesta), [mutate]);
  const azzera = useCallback(() => {
    reset();
    setIdLotto(null);
  }, [reset]);

  return {
    lotto,
    inviando: invio.isPending,
    erroreInvio: invio.error,
    errorePolling: stato.error,
    inElaborazione: invio.isPending || (lotto !== null && !lotto.completato),
    avvia,
    azzera,
  };
}

/**
 * Persona da mostrare dopo un caricamento: quella del primo file acquisito, altrimenti quella del primo file
 * attribuito a una persona (duplicato o aggiuntivo di un cedolino gia' presente).
 */
export function personaDelLotto(lotto: LottoCaricamento): number | null {
  const attribuiti = lotto.caricamenti.filter((c) => c.personaId !== null);
  return (attribuiti.find((c) => c.stato === 'ACQUISITO') ?? attribuiti[0])?.personaId ?? null;
}

const PRIORITA_ESITO: Record<Caricamento['stato'], number> = {
  ERRORE: 0,
  AGGIUNTIVO_NON_GESTITO: 1,
  DUPLICATO: 2,
  IN_LETTURA: 3,
  IN_CODA: 4,
  ACQUISITO: 5,
};

/**
 * File del lotto nell'ordine in cui mostrarli: durante l'elaborazione quello di invio; a lotto concluso prima
 * i file che richiedono attenzione (errori, aggiuntivi, duplicati), poi gli acquisiti, mantenendo l'ordine di
 * invio a parita' di esito.
 */
export function caricamentiInEvidenza(lotto: LottoCaricamento): Caricamento[] {
  if (!lotto.completato) {
    return lotto.caricamenti;
  }
  return lotto.caricamenti.toSorted((a, b) => PRIORITA_ESITO[a.stato] - PRIORITA_ESITO[b.stato]);
}
