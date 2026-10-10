import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import type { FetchApi } from '../api/client';
import { Providers } from '../Providers';
import { creaQueryClient } from '../queryClient';
import { creaMockBackend, type OpzioniMock } from '../mocks/backend';

interface OpzioniRender extends OpzioniMock {
  /** Avvolge il fetch del backend simulato, per esempio per simulare un errore del server. */
  avvolgiFetch?: (fetchApi: FetchApi) => FetchApi;
}

/** Monta un componente con tutti i provider e il backend simulato in memoria. */
export function renderConMock(ui: ReactElement, { avvolgiFetch, ...opzioni }: OpzioniRender = {}) {
  const backend = creaMockBackend(opzioni);
  const queryClient = creaQueryClient();
  queryClient.setDefaultOptions({
    queries: { ...queryClient.getDefaultOptions().queries, retry: false },
  });
  const user = userEvent.setup();
  const risultato = render(
    <Providers
      fetchApi={avvolgiFetch ? avvolgiFetch(backend.fetch) : backend.fetch}
      queryClient={queryClient}
      ambiente="test"
    >
      {ui}
    </Providers>,
  );
  return { ...risultato, backend, queryClient, user };
}
