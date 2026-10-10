import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { chiavi } from './chiavi';
import type { ApiClient } from './client';
import { useApiClient } from './context';
import { esito } from './errors';
import { invalidaDopoAcquisizione, invalidaDopoModificaFileInErrore } from './invalidazioni';
import type { CedolinoManuale, LottoCaricamento, RapportoDettaglio, RichiestaAttribuzione } from './types';

export { chiavi };

// ------------------------------------------------------------------ letture

export function usePersone() {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.persone,
    queryFn: async ({ signal }) => esito(await api.GET('/api/v1/persone', { signal })),
  });
}

export function useRapporti(personaId: number | null) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.rapporti(personaId ?? 0),
    queryFn: async ({ signal }) =>
      esito(
        await api.GET('/api/v1/persone/{personaId}/rapporti', {
          params: { path: { personaId: personaId ?? 0 } },
          signal,
        }),
      ),
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
export function useCaricamentiInErrore() {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.caricamentiInErrore,
    queryFn: async ({ signal }) => esito(await api.GET('/api/v1/caricamenti/in-errore', { signal })),
  });
}

/**
 * Rilegge il PDF conservato di un file in errore in un nuovo lotto di un solo file (eventualmente con la password,
 * mai salvata). Il lotto restituito si segue con `useCaricamento().segui`; alla fine del lotto l'elenco dei file in
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

export interface RichiestaCedolinoManuale {
  caricamentoId: number;
  dati: CedolinoManuale;
}

/** Prova dell'inserimento a mano: nessun dato salvato, quindi nessuna invalidazione. */
export function useVerificaCedolinoManuale() {
  const api = useApiClient();
  return useMutation({
    mutationFn: async ({ caricamentoId, dati }: RichiestaCedolinoManuale) =>
      esito(
        await api.POST('/api/v1/caricamenti/{caricamentoId}/cedolino/verifica', {
          params: { path: { caricamentoId } },
          body: dati,
        }),
      ),
  });
}

/**
 * Inserimento a mano del cedolino di un file in errore. A salvataggio riuscito rilegge tutto cio' che un'acquisizione
 * cambia e il lotto del file (che ora risulta ACQUISITO), prima di risolvere la mutazione.
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
      queryClient.removeQueries({ queryKey: chiavi.pdfCaricamento(caricamentoId) });
      await Promise.all([
        invalidaDopoAcquisizione(queryClient),
        queryClient.invalidateQueries({ queryKey: chiavi.lotto(caricamento.lotto) }),
      ]);
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
      queryClient.removeQueries({ queryKey: chiavi.pdfCaricamento(caricamentoId) });
      await invalidaDopoModificaFileInErrore(queryClient);
    },
  });
}
