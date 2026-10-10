import { testi } from '../i18n';
import type { Problema } from './types';

/** Errore di una chiamata REST, con i dati Problem Details (RFC 9457) quando il backend li fornisce. */
export class ApiError extends Error {
  readonly status: number;
  readonly problema: Partial<Problema> | null;

  constructor(status: number, problema: Partial<Problema> | null) {
    super(problema?.detail ?? problema?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problema = problema;
  }
}

function comeProblema(valore: unknown): Partial<Problema> | null {
  if (typeof valore !== 'object' || valore === null) {
    return null;
  }
  const { title, detail } = valore as Record<string, unknown>;
  return {
    title: typeof title === 'string' ? title : undefined,
    detail: typeof detail === 'string' ? detail : null,
  };
}

/** Converte l'esito di openapi-fetch in dato o eccezione. */
export function esito<T>(risultato: { data?: T; error?: unknown; response: Response }): T {
  if (risultato.response.ok && risultato.error === undefined) {
    return risultato.data as T;
  }
  throw new ApiError(risultato.response.status, comeProblema(risultato.error));
}

/** Messaggio leggibile per l'utente, senza dettagli tecnici. */
export function messaggioErrore(errore: unknown): string {
  if (errore instanceof ApiError) {
    return errore.problema?.detail ?? errore.problema?.title ?? testi.comune.erroreGenerico;
  }
  if (errore instanceof TypeError) {
    // fetch rifiuta con TypeError quando il server non e' raggiungibile
    return testi.comune.erroreRete;
  }
  return testi.comune.erroreGenerico;
}
