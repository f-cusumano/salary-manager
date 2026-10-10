import '@mantine/core/styles.css';
import '@mantine/dropzone/styles.css';
import '@mantine/notifications/styles.css';
import './styles.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import type { FetchApi } from './api/client';
import { Providers } from './Providers';

async function avvia() {
  let fetchApi: FetchApi | undefined;
  // i dati simulati esistono solo con `--mode mock`: in produzione questo ramo (e il modulo) sparisce dal bundle
  if (import.meta.env.VITE_USE_MOCKS === 'true') {
    const { creaFetchMock } = await import('./mocks');
    fetchApi = creaFetchMock();
  }
  const radice = document.getElementById('root');
  if (!radice) {
    throw new Error('Elemento #root mancante');
  }
  createRoot(radice).render(
    <StrictMode>
      <Providers {...(fetchApi ? { fetchApi } : {})}>
        <App />
      </Providers>
    </StrictMode>,
  );
}

void avvia();
