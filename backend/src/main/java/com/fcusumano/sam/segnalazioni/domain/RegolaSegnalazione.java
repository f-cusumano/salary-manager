package com.fcusumano.sam.segnalazioni.domain;

import java.util.List;

/**
 * Regola di anomalia (strategy). Ogni regola e' una classe separata, pura e testabile singolarmente; le regole sono
 * registrate come bean nell'application layer. Una regola deve restituire solo segnalazioni certe: in caso di dati
 * insufficienti o ambigui non segnala.
 */
public interface RegolaSegnalazione {

    /** Codice stabile della regola (es. "MENSILITA_MANCANTE"). */
    String codice();

    /** Valuta la regola sul rapporto; lista vuota se nessuna anomalia. */
    List<SegnalazioneRilevata> valuta(ContestoRapporto contesto);
}
