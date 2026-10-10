package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.util.List;

/**
 * Esito di una regola (non ancora persistito).
 *
 * @param codiceRegola codice della regola che l'ha prodotta (vedi {@link RegolaSegnalazione#codice()})
 */
public record SegnalazioneRilevata(
        String codiceRegola,
        TipoSegnalazione tipo,
        Gravita gravita,
        CedolinoId cedolinoId,
        Competenza.Mensilita competenza,
        String titolo,
        String descrizione,
        List<Evidenza> evidenze) {

    public SegnalazioneRilevata {
        evidenze = evidenze == null ? List.of() : List.copyOf(evidenze);
    }
}
