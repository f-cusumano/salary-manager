package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.util.Set;

/**
 * Evento: sono cambiate le mensilita' con un file in errore associato di questi rapporti (associazione creata,
 * spostata o rimossa; file archiviato, scartato o rimosso). Pubblicato nella transazione della modifica: il modulo
 * segnalazioni ricalcola le segnalazioni dei rapporti in modo sincrono.
 */
public record CaricamentiInErroreModificati(Set<RapportoLavoroId> rapporti) {

    public CaricamentiInErroreModificati {
        rapporti = Set.copyOf(rapporti);
    }
}
