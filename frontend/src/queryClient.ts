import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api/errors';

/** Cache delle query con i default dell'applicazione. */
export function creaQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // un 404 o un 400 non si risolvono riprovando
        retry: (tentativi, errore) => !(errore instanceof ApiError && errore.status < 500) && tentativi < 2,
      },
      mutations: { retry: false },
    },
  });
}
