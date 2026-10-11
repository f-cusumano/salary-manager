import { createElement, type ReactNode } from 'react';
import { creaApiClient } from '../api/client';
import { creaMockBackend } from '../mocks/backend';
import type { DatiIniziali } from '../mocks/dati';
import { Providers } from '../Providers';
import { creaQueryClient } from '../queryClient';

/** Provider, cache delle query e backend simulato per provare un hook con renderHook. */
export function contestoHook(datiIniziali: DatiIniziali = 'errori') {
  const backend = creaMockBackend({ datiIniziali });
  const queryClient = creaQueryClient();
  queryClient.setDefaultOptions({
    queries: { ...queryClient.getDefaultOptions().queries, retry: false },
  });
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(Providers, { fetchApi: backend.fetch, queryClient, ambiente: 'test', children });
  return { backend, queryClient, wrapper, api: creaApiClient(backend.fetch) };
}
