package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Evento: un cedolino e' stato eliminato. Pubblicato nella transazione di eliminazione, dopo la cancellazione (e
 * prima dell'eventuale eliminazione del rapporto rimasto senza cedolini): i dati derivati vengono ricalcolati da zero.
 */
public record CedolinoEliminato(CedolinoId cedolinoId, RapportoLavoroId rapportoId, Competenza competenza) {
}
