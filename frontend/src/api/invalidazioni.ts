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
