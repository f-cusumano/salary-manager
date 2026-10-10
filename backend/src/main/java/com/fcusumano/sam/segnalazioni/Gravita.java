package com.fcusumano.sam.segnalazioni;

/**
 * Gravita' di una segnalazione.
 */
public enum Gravita {
    /** Informazione utile, non un'anomalia (es. CCNL non determinato). */
    INFORMATIVA,
    /** Possibile anomalia da verificare (es. mensilita' mancante). */
    AVVISO,
    /** Anomalia accertata sui dati (es. fondo TFR incoerente). */
    ANOMALIA
}
