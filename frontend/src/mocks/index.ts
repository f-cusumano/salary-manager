import { creaMockBackend } from './backend';
import type { DatiIniziali } from './dati';

/**
 * Attiva il backend simulato in sviluppo (`npm run dev:mock`). Con `?mock=vuoto` nell'URL parte senza dati, come al
 * primo avvio dell'applicazione; con `?mock=errori` ai dati dimostrativi si aggiungono tre cedolini caricati con
 * errore.
 */
export function creaFetchMock() {
  const scelta = new URLSearchParams(window.location.search).get('mock');
  const datiIniziali: DatiIniziali = scelta === 'vuoto' || scelta === 'errori' ? scelta : 'demo';
  return creaMockBackend({ ritardoMs: 350, datiIniziali }).fetch;
}
