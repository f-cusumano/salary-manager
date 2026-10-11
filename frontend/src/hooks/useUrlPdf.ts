import { useQuery } from '@tanstack/react-query';
import { useCallback, useSyncExternalStore } from 'react';
import { chiavi } from '../api/chiavi';
import { useApiClient } from '../api/context';
import { leggiPdfCaricamento } from '../api/download';

interface UrlCondiviso {
  url: string;
  riferimenti: number;
}

/**
 * URL `blob:` dei PDF mostrati, con il numero di componenti che li usano: l'URL nasce con il primo e viene revocato
 * quando l'ultimo smette di usarlo (anche nel doppio montaggio di StrictMode, che lo ricrea).
 */
const urlPerContenuto = new WeakMap<Blob, UrlCondiviso>();

function usaUrl(contenuto: Blob | null, notifica: () => void): () => void {
  if (contenuto === null) {
    return () => undefined;
  }
  const esistente = urlPerContenuto.get(contenuto);
  if (esistente) {
    esistente.riferimenti += 1;
  } else {
    urlPerContenuto.set(contenuto, { url: URL.createObjectURL(contenuto), riferimenti: 1 });
  }
  notifica();
  return () => {
    const condiviso = urlPerContenuto.get(contenuto);
    if (condiviso) {
      condiviso.riferimenti -= 1;
      if (condiviso.riferimenti === 0) {
        urlPerContenuto.delete(contenuto);
        URL.revokeObjectURL(condiviso.url);
      }
    }
  };
}

/**
 * PDF conservato di un file in errore come URL `blob:` per l'anteprima in un iframe (un URL del backend verrebbe
 * bloccato da X-Frame-Options ed e' servito come allegato). Il PDF non resta in cache dopo la chiusura
 * dell'anteprima (gcTime 0) e l'URL viene revocato allo smontaggio o al cambio di contenuto.
 */
export function useUrlPdf(caricamentoId: number) {
  const api = useApiClient();
  const pdf = useQuery({
    queryKey: chiavi.pdfCaricamento(caricamentoId),
    queryFn: ({ signal }) => leggiPdfCaricamento(api, caricamentoId, signal),
    staleTime: Infinity,
    gcTime: 0,
  });
  const contenuto = pdf.data?.contenuto ?? null;
  const sottoscrivi = useCallback((notifica: () => void) => usaUrl(contenuto, notifica), [contenuto]);
  const url = useSyncExternalStore(sottoscrivi, () =>
    contenuto !== null ? (urlPerContenuto.get(contenuto)?.url ?? null) : null,
  );
  const { refetch } = pdf;
  const riprova = useCallback(() => void refetch(), [refetch]);

  return {
    /** URL `blob:` del PDF, null finche' non e' pronto. */
    url,
    /** Nome originale del file indicato dal backend. */
    nome: pdf.data?.nome ?? null,
    caricando: pdf.isPending,
    errore: pdf.error,
    riprova,
  };
}
