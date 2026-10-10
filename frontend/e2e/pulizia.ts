import { rmSync } from 'node:fs';

/** Elimina il database temporaneo creato per la suite (non esiste se si usa uno stack esterno). */
export default function pulizia(): void {
  const cartella = process.env.SAM_E2E_CARTELLA_DB;
  if (!cartella) {
    return;
  }
  try {
    rmSync(cartella, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 });
  } catch (errore) {
    // su Windows il backend puo' tenere ancora aperto il file H2: la cartella resta tra i file temporanei
    console.warn(`Database temporaneo non eliminato (${cartella}): ${String(errore)}`);
  }
}
