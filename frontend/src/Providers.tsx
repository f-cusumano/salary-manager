import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { creaApiClient, type FetchApi } from './api/client';
import { ApiClientContext } from './api/context';
import { creaQueryClient } from './queryClient';
import { theme } from './theme';

interface Props {
  children: ReactNode;
  fetchApi?: FetchApi;
  queryClient?: QueryClient;
  /** "test" disattiva transizioni e portali di Mantine (solo nei test). */
  ambiente?: 'default' | 'test';
}

/** Tema Mantine, client REST, cache delle query, modali e notifiche. */
export function Providers({ children, fetchApi, queryClient, ambiente = 'default' }: Props) {
  const [client] = useState(() => queryClient ?? creaQueryClient());
  const [api] = useState(() => creaApiClient(fetchApi));
  return (
    <MantineProvider theme={theme} defaultColorScheme="light" env={ambiente}>
      <QueryClientProvider client={client}>
        <ApiClientContext.Provider value={api}>
          <ModalsProvider labels={{ confirm: 'OK', cancel: 'Annulla' }}>
            <Notifications position="bottom-right" limit={4} />
            {children}
          </ModalsProvider>
        </ApiClientContext.Provider>
      </QueryClientProvider>
    </MantineProvider>
  );
}
