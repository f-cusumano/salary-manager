import { createContext, useContext } from 'react';
import type { ApiClient } from './client';

export const ApiClientContext = createContext<ApiClient | null>(null);

export function useApiClient(): ApiClient {
  const client = useContext(ApiClientContext);
  if (!client) {
    throw new Error('ApiClientContext non configurato');
  }
  return client;
}
