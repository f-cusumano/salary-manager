import type { ApiClient } from './client';
import { esito } from './errors';

/** Estrae il nome file dall'header Content-Disposition (anche nella forma RFC 5987 filename*=UTF-8''...). */
export function nomeDaContentDisposition(header: string | null): string | null {
  if (!header) {
    return null;
  }
  const esteso = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (esteso?.[1]) {
    try {
      return decodeURIComponent(esteso[1].trim());
    } catch {
      // codifica non valida: si prova con il parametro semplice
    }
  }
  const semplice = /filename\s*=\s*"?([^";]+)"?/i.exec(header);
  return semplice?.[1]?.trim() ?? null;
}

/** PDF letto dal backend: contenuto e nome indicato nell'header Content-Disposition (null se assente). */
export interface PdfLetto {
  contenuto: Blob;
  nome: string | null;
}

type RispostaBinaria = Promise<{ data?: Blob; error?: unknown; response: Response }>;

/**
 * Legge la risposta di un endpoint PDF (`parseAs: 'blob'`). In caso di errore openapi-fetch legge comunque il Problem
 * Details: il motivo arriva all'utente come ApiError.
 */
export async function leggiPdf(chiamata: RispostaBinaria): Promise<PdfLetto> {
  const risultato = await chiamata;
  const data = esito(risultato);
  return {
    // il tipo esplicito serve all'anteprima: un Blob senza tipo verrebbe scaricato invece che mostrato
    contenuto: data.type === 'application/pdf' ? data : new Blob([data], { type: 'application/pdf' }),
    nome: nomeDaContentDisposition(risultato.response.headers.get('Content-Disposition')),
  };
}

/** PDF originale conservato di un file caricato con errore (anche se protetto da password), con il nome originale. */
export function leggiPdfCaricamento(
  api: ApiClient,
  caricamentoId: number,
  signal?: AbortSignal,
): Promise<PdfLetto> {
  return leggiPdf(
    api.GET('/api/v1/caricamenti/{caricamentoId}/pdf', {
      params: { path: { caricamentoId } },
      parseAs: 'blob',
      signal,
    }),
  );
}

/** PDF archiviato di un cedolino, con il nome normalizzato (anteprima della modifica a mano). */
export function leggiPdfCedolino(
  api: ApiClient,
  cedolinoId: number,
  signal?: AbortSignal,
): Promise<PdfLetto> {
  return leggiPdf(
    api.GET('/api/v1/cedolini/{cedolinoId}/pdf', {
      params: { path: { cedolinoId } },
      parseAs: 'blob',
      signal,
    }),
  );
}

/** Fa salvare al browser un contenuto con il nome indicato (link temporaneo con attributo download). */
function salvaFile(contenuto: Blob, nome: string): void {
  const url = URL.createObjectURL(contenuto);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.rel = 'noopener';
    document.body.append(link);
    link.click();
    link.remove();
  } finally {
    // il browser ha gia' preso in carico il download: l'URL temporaneo si puo' liberare subito dopo
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

/** Scarica il PDF originale di un cedolino con il nome normalizzato indicato dal backend. */
export async function scaricaPdfCedolino(
  api: ApiClient,
  cedolinoId: number,
  nomeFileRiserva: string,
): Promise<void> {
  const pdf = await leggiPdf(
    api.GET('/api/v1/cedolini/{cedolinoId}/pdf', { params: { path: { cedolinoId } }, parseAs: 'blob' }),
  );
  salvaFile(pdf.contenuto, pdf.nome ?? nomeFileRiserva);
}

/** Scarica il PDF conservato di un file caricato con errore, con il nome con cui era stato caricato. */
export async function scaricaPdfCaricamento(
  api: ApiClient,
  caricamentoId: number,
  nomeFileRiserva: string,
): Promise<void> {
  const pdf = await leggiPdfCaricamento(api, caricamentoId);
  salvaFile(pdf.contenuto, pdf.nome ?? nomeFileRiserva);
}
