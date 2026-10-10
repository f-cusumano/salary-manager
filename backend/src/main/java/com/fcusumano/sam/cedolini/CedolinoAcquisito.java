package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Evento: un cedolino e' stato salvato. Pubblicato nella transazione di salvataggio, dopo la scrittura: i moduli
 * calcoli e segnalazioni ricalcolano da zero i dati derivati del rapporto in modo sincrono.
 */
public record CedolinoAcquisito(CedolinoId cedolinoId, RapportoLavoroId rapportoId, Competenza competenza) {
}
