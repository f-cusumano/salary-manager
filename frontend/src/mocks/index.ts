import { creaMockBackend } from './backend';

/**
 * Attiva il backend simulato in sviluppo (`npm run dev:mock`). Con `?mock=vuoto` nell'URL parte senza dati, come al
 * primo avvio dell'applicazione.
 */
export function creaFetchMock() {
  const vuoto = new URLSearchParams(window.location.search).get('mock') === 'vuoto';
  return creaMockBackend({ ritardoMs: 350, datiIniziali: vuoto ? 'vuoto' : 'demo' }).fetch;
}
