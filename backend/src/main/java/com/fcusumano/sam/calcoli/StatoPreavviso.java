package com.fcusumano.sam.calcoli;

/**
 * Esito della ricerca dei termini di preavviso del rapporto.
 */
public enum StatoPreavviso {
    /** Termini trovati nella tabella dei CCNL verificati per il livello e l'anzianita' del rapporto. */
    VERIFICATO,
    /** CCNL del rapporto da indicare. */
    CCNL_DA_INDICARE,
    /** Il CCNL non ha ancora termini di preavviso verificati su due fonti. */
    CCNL_NON_VERIFICATO,
    /** Livello non stampato sui cedolini. */
    LIVELLO_NON_INDICATO,
    /** Livello stampato non riconosciuto tra quelli del CCNL. */
    LIVELLO_NON_RICONOSCIUTO,
    /** Anzianita' aziendale non determinabile. */
    ANZIANITA_NON_DETERMINABILE
}
