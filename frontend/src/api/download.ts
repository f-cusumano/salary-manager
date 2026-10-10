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

/** Scarica il PDF originale di un cedolino con il nome normalizzato indicato dal backend. */
export async function scaricaPdfCedolino(
  api: ApiClient,
  cedolinoId: number,
  nomeFileRiserva: string,
): Promise<void> {
  const risultato = await api.GET('/api/v1/cedolini/{cedolinoId}/pdf', {
    params: { path: { cedolinoId } },
    parseAs: 'blob',
  });
  // in caso di errore openapi-fetch legge comunque il Problem Details: il motivo arriva all'utente
  const data = esito(risultato);
  const nome =
    nomeDaContentDisposition(risultato.response.headers.get('Content-Disposition')) ?? nomeFileRiserva;
  const url = URL.createObjectURL(data);
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
