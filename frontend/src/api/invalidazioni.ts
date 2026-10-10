import type { QueryClient } from '@tanstack/react-query';
import { chiavi } from './chiavi';

/**
 * Dopo l'acquisizione di cedolini (fine di un lotto, anche di una rilettura, o inserimento a mano): nuove persone,
 * aziende e mensilita', segnalazioni ricalcolate, file non piu' in errore.
 */
export async function invalidaDopoAcquisizione(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chiavi.persone }),
    queryClient.invalidateQueries({ queryKey: chiavi.rapportiTutti }),
    queryClient.invalidateQueries({ queryKey: chiavi.rapportoTutti }),
    // un nuovo cedolino puo' riclassificare quelli gia' presenti (progressivo TFR e nome del file)
    queryClient.invalidateQueries({ queryKey: chiavi.cedolinoTutti }),
    // un file acquisito esce dall'elenco; uno nuovo in errore vi entra
    queryClient.invalidateQueries({ queryKey: chiavi.caricamentiInErrore }),
  ]);
}

/**
 * Dopo l'associazione di un file in errore a una mensilita', la sua rimozione o la rimozione del file: cambiano le
 * righe delle mensilita' (MANCANTE / IN_ERRORE) e le segnalazioni dei rapporti coinvolti.
 */
export async function invalidaDopoModificaFileInErrore(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chiavi.rapportoTutti }),
    // la riga dell'azienda segnala la presenza di segnalazioni
    queryClient.invalidateQueries({ queryKey: chiavi.rapportiTutti }),
    queryClient.invalidateQueries({ queryKey: chiavi.caricamentiInErrore }),
  ]);
}

/**
 * Toglie dalla cache il PDF conservato di un file che non e' piu' in errore (inserito a mano o rimosso). Se l'anteprima
 * e' ancora aperta il PDF resta finche' non si chiude (gcTime 0): toglierlo sotto un'anteprima montata la farebbe
 * ricaricare dal backend, che non lo conserva piu' (404).
 */
export function scartaPdfConservato(queryClient: QueryClient, caricamentoId: number): void {
  const queryKey = chiavi.pdfCaricamento(caricamentoId);
  if (!queryClient.getQueryCache().find({ queryKey, exact: true })?.getObserversCount()) {
    queryClient.removeQueries({ queryKey, exact: true });
  }
}
