import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { ApiClient } from './client';
import { useApiClient } from './context';
import { esito } from './errors';
import type { LottoCaricamento, RapportoDettaglio } from './types';

/** Chiavi di cache di TanStack Query. */
export const chiavi = {
  persone: ['persone'] as const,
  rapportiTutti: ['rapporti'] as const,
  rapporti: (personaId: number) => ['rapporti', personaId] as const,
  rapportoTutti: ['rapporto'] as const,
  rapporto: (rapportoId: number) => ['rapporto', rapportoId] as const,
  cedolinoTutti: ['cedolino'] as const,
  cedolino: (cedolinoId: number) => ['cedolino', cedolinoId] as const,
  ccnl: ['ccnl'] as const,
  lotto: (lotto: string) => ['lotto', lotto] as const,
};

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

export function useRapporto(rapportoId: number) {
  const api = useApiClient();
  return useQuery({
    queryKey: chiavi.rapporto(rapportoId),
    queryFn: ({ signal }) => leggiRapporto(api, rapportoId, signal),
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
      // l'eliminazione puo' rinumerare i cedolini di fine rapporto rimasti (progressivo e nome del file)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chiavi.rapportoTutti }),
        queryClient.invalidateQueries({ queryKey: chiavi.cedolinoTutti }),
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
