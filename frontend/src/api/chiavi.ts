/** Chiavi di cache di TanStack Query (modulo a se' perche' le usano sia le query sia le invalidazioni). */
export const chiavi = {
  persone: ['persone'] as const,
  aziende: ['aziende'] as const,
  rapportiTutti: ['rapporti'] as const,
  rapporti: (personaId: number) => ['rapporti', personaId] as const,
  rapportoTutti: ['rapporto'] as const,
  rapporto: (rapportoId: number) => ['rapporto', rapportoId] as const,
  cedolinoTutti: ['cedolino'] as const,
  cedolino: (cedolinoId: number) => ['cedolino', cedolinoId] as const,
  ccnl: ['ccnl'] as const,
  lotto: (lotto: string) => ['lotto', lotto] as const,
  caricamentiInErrore: ['caricamenti', 'in-errore'] as const,
  pdfCaricamento: (caricamentoId: number) => ['caricamento-pdf', caricamentoId] as const,
};
