package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Costruzione delle evidenze delle segnalazioni (cedolini e valori coinvolti, gia' formattati).
 */
final class Evidenze {

    private Evidenze() {
    }

    /** Il cedolino stesso come evidenza, es. "Cedolino di Aprile 2023". */
    static Evidenza cedolino(String ruolo, CedolinoNormalizzato cedolino) {
        return new Evidenza(ruolo + ": " + Formato.competenza(cedolino.competenza()), cedolino.id(),
                cedolino.competenza(), null);
    }

    /** Un importo letto su un cedolino, es. "Fondo TFR al 31/12/2023" = "4.526,32 €". */
    static Evidenza importo(String descrizione, CedolinoNormalizzato cedolino, BigDecimal importo) {
        return new Evidenza(descrizione + " (" + Formato.competenza(cedolino.competenza()) + ")", cedolino.id(),
                cedolino.competenza(), Formato.euro(importo));
    }

    /** Un valore testuale letto su un cedolino (livello, numero di scatti, date). */
    static Evidenza valore(String descrizione, CedolinoNormalizzato cedolino, String valore) {
        return new Evidenza(descrizione + " (" + Formato.competenza(cedolino.competenza()) + ")", cedolino.id(),
                cedolino.competenza(), valore);
    }

    /** Un valore del rapporto non legato a un cedolino (es. data di cessazione). */
    static Evidenza rapporto(String descrizione, String valore) {
        return new Evidenza(descrizione, null, null, valore);
    }

    static Evidenza data(String descrizione, LocalDate data) {
        return rapporto(descrizione, Formato.data(data));
    }
}
