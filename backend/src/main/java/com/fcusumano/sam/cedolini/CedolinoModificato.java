package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Cedolino archiviato di nuovo con i dati modificati a mano.
 *
 * @param cedolinoId        nuovo id del cedolino
 * @param rapportoId        rapporto del cedolino (diverso dal precedente se sono cambiate persona o azienda)
 * @param competenza        competenza con cui e' archiviato
 * @param rapportoEliminato il rapporto precedente e' stato eliminato perche' rimasto senza cedolini
 */
public record CedolinoModificato(CedolinoId cedolinoId, RapportoLavoroId rapportoId, Competenza competenza,
                                 boolean rapportoEliminato) {
}
