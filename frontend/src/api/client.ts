import createClient from 'openapi-fetch';
import type { paths } from './schema';

export type ApiClient = ReturnType<typeof createClient<paths>>;

/** Funzione fetch iniettabile (mock in sviluppo e nei test). */
export type FetchApi = (request: Request) => Promise<Response>;

/**
 * Crea il client tipizzato sul contratto OpenAPI. I path includono gia' `/api/v1`: in sviluppo Vite li inoltra al
 * backend, in produzione li serve lo stesso host del frontend (reverse proxy). `VITE_API_BASE_URL` permette di
 * puntare a un backend diverso.
 */
export function creaApiClient(fetchApi?: FetchApi): ApiClient {
  const configurato = import.meta.env.VITE_API_BASE_URL;
  const baseUrl = configurato && configurato.length > 0 ? configurato : window.location.origin;
  return createClient<paths>({
    baseUrl,
    ...(fetchApi ? { fetch: fetchApi } : {}),
  });
}
