import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { chiavi } from './chiavi';
import type { ApiClient } from './client';
import { useApiClient } from './context';
import { esito } from './errors';
import {
  invalidaDopoAcquisizione,
  invalidaDopoModificaFileInErrore,
  invalidaSeFileInErroreCambiato,
  scartaPdfConservato,
} from './invalidazioni';
import type {
  CedolinoManuale,
  CedolinoModificato,
  LottoCaricamento,
  RapportoDettaglio,
  RichiestaAttribuzione,
} from './types';

export { chiavi };

// ------------------------------------------------------------------ letture

export function usePersone() {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.persone,
    queryFn: async ({ signal }) => esito(await api.GET('/api/v1/persone', { signal })),
  });
}

/**
 * Aziende (datori di lavoro) per la scelta nell'inserimento a mano, ordinate per ragione sociale, con l'etichetta
 * anti-omonimia calcolata dal backend su tutte le aziende.
 */
export function useAziende(abilitato = true) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.aziende,
    queryFn: async ({ signal }) => esito(await api.GET('/api/v1/aziende', { signal })),
    enabled: abilitato,
  });
}

function leggiRapporti(api: ApiClient, personaId: number, signal?: AbortSignal) {
  return api
    .GET('/api/v1/persone/{personaId}/rapporti', { params: { path: { personaId } }, signal })
    .then((r) => esito(r));
}

export function useRapporti(personaId: number | null) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.rapporti(personaId ?? 0),
    queryFn: ({ signal }) => leggiRapporti(api, personaId ?? 0, signal),
    enabled: personaId !== null,
  });
}

function leggiRapporto(api: ApiClient, rapportoId: number, signal?: AbortSignal) {
  return api
    .GET('/api/v1/rapporti/{rapportoId}', { params: { path: { rapportoId } }, signal })
    .then((r) => esito(r));
}

export function useRapporto(rapportoId: number, abilitato = true) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.rapporto(rapportoId),
    queryFn: ({ signal }) => leggiRapporto(api, rapportoId, signal),
    enabled: abilitato,
  });
}

function leggiCedolino(api: ApiClient, cedolinoId: number, signal?: AbortSignal) {
  return api
    .GET('/api/v1/cedolini/{cedolinoId}', { params: { path: { cedolinoId } }, signal })
    .then((r) => esito(r));
}

/**
 * Dettaglio busta caricato solo quando la barra viene espansa. Non e' immutabile: caricamenti, eliminazioni e cambi di
 * CCNL possono riclassificare i cedolini del rapporto (competenza, progressivo TFR, nome del file).
 */
export function useCedolino(cedolinoId: number, abilitato: boolean) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.cedolino(cedolinoId),
    queryFn: ({ signal }) => leggiCedolino(api, cedolinoId, signal),
    enabled: abilitato,
  });
}

/**
 * Letture a richiesta (non legate al disegno di un componente) con la cache condivisa: stesse chiavi e stesse funzioni
 * degli hook, quindi un dato gia' letto e recente non viene richiesto di nuovo.
 */
export function useLetture() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMemo(
    () => ({
      rapporti: (personaId: number) =>
        queryClient.query({
          queryKey: chiavi.rapporti(personaId),
          queryFn: ({ signal }) => leggiRapporti(api, personaId, signal),
        }),
      rapporto: (rapportoId: number) =>
        queryClient.query({
          queryKey: chiavi.rapporto(rapportoId),
          queryFn: ({ signal }) => leggiRapporto(api, rapportoId, signal),
        }),
      cedolino: (cedolinoId: number) =>
        queryClient.query({
          queryKey: chiavi.cedolino(cedolinoId),
          queryFn: ({ signal }) => leggiCedolino(api, cedolinoId, signal),
        }),
    }),
    [api, queryClient],
  );
}

export function useElencoCcnl(abilitato: boolean) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.ccnl,
    queryFn: async ({ signal }) => esito(await api.GET('/api/v1/ccnl', { signal })),
    enabled: abilitato,
    staleTime: Infinity,
  });
}

// ------------------------------------------------------------------ modifiche

function aggiornaSintesiRapporto(queryClient: QueryClient, dettaglio: RapportoDettaglio) {
  queryClient.setQueryData(chiavi.rapporto(dettaglio.id), dettaglio);
  // la riga dell'azienda mostra triangolo e "CCNL da indicare": va riletta
  void queryClient.invalidateQueries({ queryKey: chiavi.rapporti(dettaglio.persona.id) });
  // il ricalcolo puo' riclassificare i cedolini del rapporto
  void queryClient.invalidateQueries({ queryKey: chiavi.cedolinoTutti });
}

export function useAggiornaNote(rapportoId: number) {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (note: string | null) =>
      esito(
        await api.PUT('/api/v1/rapporti/{rapportoId}/note', {
          params: { path: { rapportoId } },
          body: { note },
        }),
      ),
    onSuccess: (salvate) => {
      queryClient.setQueryData<RapportoDettaglio>(chiavi.rapporto(rapportoId), (attuale) =>
        attuale ? { ...attuale, note: salvate.note } : attuale,
      );
    },
  });
}

export function useImpostaCcnl(rapportoId: number) {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (ccnlCodice: string) =>
      esito(
        await api.PUT('/api/v1/rapporti/{rapportoId}/ccnl', {
          params: { path: { rapportoId } },
          body: { ccnlCodice },
        }),
      ),
    onSuccess: (dettaglio) => aggiornaSintesiRapporto(queryClient, dettaglio),
  });
}

export function useEliminaCedolino() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (cedolinoId: number) => {
      const risposta = await api.DELETE('/api/v1/cedolini/{cedolinoId}', {
        params: { path: { cedolinoId } },
      });
      if (!risposta.response.ok) {
        esito(risposta);
      }
      return cedolinoId;
    },
    onSuccess: async (cedolinoId) => {
      queryClient.removeQueries({ queryKey: chiavi.cedolino(cedolinoId) });
      // se era l'ultimo cedolino il backend elimina anche rapporto e persona/azienda orfane: prima si rileggono
      // persone e aziende (un dettaglio non piu' esistente viene smontato), poi i dettagli ancora visibili
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chiavi.persone }),
        queryClient.invalidateQueries({ queryKey: chiavi.aziende }),
        queryClient.invalidateQueries({ queryKey: chiavi.rapportiTutti }),
      ]);
      // l'eliminazione puo' rinumerare i cedolini di fine rapporto rimasti (progressivo e nome del file); un file in
      // errore associato al mese eliminato torna da completare e, se sparisce il rapporto, perde l'associazione
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chiavi.rapportoTutti }),
        queryClient.invalidateQueries({ queryKey: chiavi.cedolinoTutti }),
        queryClient.invalidateQueries({ queryKey: chiavi.caricamentiInErrore }),
      ]);
    },
  });
}

// ------------------------------------------------------------------ caricamenti

export interface RichiestaCaricamento {
  files: File[];
  /** Password di apertura dei PDF protetti, valida per tutto il lotto. */
  password: string | null;
}

export async function avviaCaricamento(
  api: ApiClient,
  { files, password }: RichiestaCaricamento,
): Promise<LottoCaricamento> {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file, file.name);
  }
  if (password) {
    formData.append('password', password);
  }
  return esito(
    await api.POST('/api/v1/caricamenti', {
      // il contratto descrive i file binari come stringhe: il corpo effettivo e' il FormData
      body: { files: [] },
      bodySerializer: () => formData,
    }),
  );
}

export async function leggiLotto(api: ApiClient, lotto: string, signal?: AbortSignal) {
  return esito(await api.GET('/api/v1/caricamenti/lotti/{lotto}', { params: { path: { lotto } }, signal }));
}

// ------------------------------------------------------------------ cedolini caricati con errore

/** File caricati ma non letti, ancora da risolvere: uno per file, dal piu' recente. */
async function leggiCaricamentiInErrore(api: ApiClient, signal?: AbortSignal) {
  return esito(await api.GET('/api/v1/caricamenti/in-errore', { signal }));
}

export function useCaricamentiInErrore() {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.caricamentiInErrore,
    queryFn: ({ signal }) => leggiCaricamentiInErrore(api, signal),
  });
}

/**
 * File in errore protetti da password che una nuova lettura puo' aprire con le password ricordate: PDF conservato e
 * nessun tentativo in corso. Elenco aggiornato (letto di nuovo se superato), cosi' alla fine di un lotto conta anche i
 * file appena usciti o rientrati in errore.
 */
export async function caricamentiProtettiDaRileggere(
  api: ApiClient,
  queryClient: QueryClient,
): Promise<number[]> {
  const elenco = await queryClient.query({
    queryKey: chiavi.caricamentiInErrore,
    queryFn: ({ signal }) => leggiCaricamentiInErrore(api, signal),
    staleTime: 0,
  });
  return elenco
    .filter((e) => e.motivo === 'PDF_PROTETTO' && e.pdfDisponibile && !e.inElaborazione)
    .map((e) => e.id);
}

/**
 * Rilegge il PDF conservato di un file in errore in un nuovo lotto di un solo file (eventualmente con la password, che
 * il backend ricorda cifrata se apre il PDF). Il lotto restituito si segue con `useCaricamento().segui`; alla fine del lotto l'elenco dei file in
 * errore viene riletto con le altre invalidazioni.
 */
export async function rileggiCaricamento(
  api: ApiClient,
  caricamentoId: number,
  password: string | null,
): Promise<LottoCaricamento> {
  return esito(
    await api.POST('/api/v1/caricamenti/{caricamentoId}/rilettura', {
      params: { path: { caricamentoId } },
      body: { password: password !== null && password.length > 0 ? password : null },
    }),
  );
}

/**
 * Rilegge in un nuovo lotto tutti i file in errore protetti da password, con le password ricordate (anche quella appena
 * indicata, se ha aperto un PDF). Null se non c'e' nulla da rileggere (204).
 */
export async function rileggiCaricamentiProtetti(api: ApiClient): Promise<LottoCaricamento | null> {
  const risposta = await api.POST('/api/v1/caricamenti/in-errore/rilettura-protetti');
  if (risposta.response.status === 204) {
    return null;
  }
  return esito(risposta);
}

export interface RichiestaCedolinoManuale {
  caricamentoId: number;
  dati: CedolinoManuale;
}

/**
 * Prova dell'inserimento a mano: nessun dato salvato, quindi nessuna invalidazione, salvo un rifiuto che rivela un
 * file in errore cambiato nel frattempo (404, 409).
 */
export function useVerificaCedolinoManuale() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ caricamentoId, dati }: RichiestaCedolinoManuale) =>
      esito(
        await api.POST('/api/v1/caricamenti/{caricamentoId}/cedolino/verifica', {
          params: { path: { caricamentoId } },
          body: dati,
        }),
      ),
    // senza attendere: l'errore si mostra subito, l'elenco si rilegge in parallelo
    onError: (errore) => void invalidaSeFileInErroreCambiato(queryClient, errore),
  });
}

/**
 * Inserimento a mano del cedolino di un file in errore. A salvataggio riuscito rilegge tutto cio' che un'acquisizione
 * cambia e il lotto del file (che ora risulta ACQUISITO), prima di risolvere la mutazione; un rifiuto 404 o 409 (file
 * cambiato nel frattempo) rilegge l'elenco dei file in errore.
 */
export function useInserisciCedolinoManuale() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ caricamentoId, dati }: RichiestaCedolinoManuale) =>
      esito(
        await api.POST('/api/v1/caricamenti/{caricamentoId}/cedolino', {
          params: { path: { caricamentoId } },
          body: dati,
        }),
      ),
    onSuccess: async (caricamento, { caricamentoId }) => {
      scartaPdfConservato(queryClient, caricamentoId);
      await Promise.all([
        invalidaDopoAcquisizione(queryClient),
        queryClient.invalidateQueries({ queryKey: chiavi.lotto(caricamento.lotto) }),
      ]);
    },
    onError: (errore) => void invalidaSeFileInErroreCambiato(queryClient, errore),
  });
}

/**
 * Dati attuali di un cedolino nella forma del modulo, per modificarli: sempre riletti all'apertura (non restano in
 * cache) perche' il modulo parta dai dati archiviati.
 */
export function useDatiCedolino(cedolinoId: number) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.datiCedolino(cedolinoId),
    queryFn: async ({ signal }) =>
      esito(
        await api.GET('/api/v1/cedolini/{cedolinoId}/dati', { params: { path: { cedolinoId } }, signal }),
      ),
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}

export interface RichiestaModificaCedolino {
  cedolinoId: number;
  dati: CedolinoManuale;
}

/** Prova della modifica dei dati di un cedolino (nulla viene salvato). */
export function useVerificaModificaCedolino() {
  const api = useApiClient();
  return useMutation({
    mutationFn: async ({ cedolinoId, dati }: RichiestaModificaCedolino) =>
      esito(
        await api.POST('/api/v1/cedolini/{cedolinoId}/dati/verifica', {
          params: { path: { cedolinoId } },
          body: dati,
        }),
      ),
  });
}

/**
 * Salvataggio dei dati modificati: il cedolino e' sostituito da uno nuovo (nuovo id), anche in un altro rapporto; il
 * rapporto precedente puo' sparire con le anagrafiche orfane. Si rilegge cio' che cambierebbero un'eliminazione e
 * un'acquisizione, prima di risolvere la mutazione.
 */
export function useModificaCedolino() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ cedolinoId, dati }: RichiestaModificaCedolino): Promise<CedolinoModificato> =>
      esito(
        await api.PUT('/api/v1/cedolini/{cedolinoId}/dati', {
          params: { path: { cedolinoId } },
          body: dati,
        }),
      ),
    onSuccess: async (_modificato, { cedolinoId }) => {
      queryClient.removeQueries({ queryKey: chiavi.cedolino(cedolinoId) });
      await invalidaDopoAcquisizione(queryClient);
    },
  });
}

export interface RichiestaAssociazione {
  caricamentoId: number;
  attribuzione: RichiestaAttribuzione;
}

/** Associa (o sposta) un file in errore a una mensilita' di un rapporto. */
export function useAssociaCaricamento() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ caricamentoId, attribuzione }: RichiestaAssociazione) =>
      esito(
        await api.PUT('/api/v1/caricamenti/{caricamentoId}/attribuzione', {
          params: { path: { caricamentoId } },
          body: attribuzione,
        }),
      ),
    onSuccess: () => invalidaDopoModificaFileInErrore(queryClient),
    onError: (errore) => void invalidaSeFileInErroreCambiato(queryClient, errore),
  });
}

export function useRimuoviAssociazione() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (caricamentoId: number) =>
      esito(
        await api.DELETE('/api/v1/caricamenti/{caricamentoId}/attribuzione', {
          params: { path: { caricamentoId } },
        }),
      ),
    onSuccess: () => invalidaDopoModificaFileInErrore(queryClient),
    onError: (errore) => void invalidaSeFileInErroreCambiato(queryClient, errore),
  });
}

/** Rimuove un file in errore: tentativi in errore, PDF conservato e associazione. */
export function useRimuoviCaricamento() {
  const api = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (caricamentoId: number) => {
      const risposta = await api.DELETE('/api/v1/caricamenti/{caricamentoId}', {
        params: { path: { caricamentoId } },
      });
      if (!risposta.response.ok) {
        esito(risposta);
      }
      return caricamentoId;
    },
    onSuccess: async (caricamentoId) => {
      scartaPdfConservato(queryClient, caricamentoId);
      await invalidaDopoModificaFileInErrore(queryClient);
    },
    onError: (errore) => void invalidaSeFileInErroreCambiato(queryClient, errore),
  });
}
