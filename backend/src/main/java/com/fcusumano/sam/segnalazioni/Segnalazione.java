package com.fcusumano.sam.segnalazioni;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.shared.Competenza;
import java.util.List;

/**
 * Segnalazione di anomalia su un rapporto.
 *
 * @param cedolinoId  cedolino a cui si riferisce, null se riguarda il rapporto nel suo insieme
 * @param competenza  competenza interessata (es. la mensilita' mancante), null se non applicabile
 * @param titolo      titolo breve (mostrato in grassetto)
 * @param descrizione descrizione per l'utente
 */
public record Segnalazione(
        SegnalazioneId id,
        RapportoLavoroId rapportoId,
        CedolinoId cedolinoId,
        Competenza.Mensilita competenza,
        TipoSegnalazione tipo,
        Gravita gravita,
        String titolo,
        String descrizione,
        List<Evidenza> evidenze) {

    public Segnalazione {
        evidenze = evidenze == null ? List.of() : List.copyOf(evidenze);
    }
}
