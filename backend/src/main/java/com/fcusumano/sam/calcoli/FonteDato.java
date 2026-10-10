package com.fcusumano.sam.calcoli;

/**
 * Origine di un dato derivato, mostrata in UI per trasparenza.
 */
public enum FonteDato {
    /** Data contrattuale (assunzione/cessazione) stampata sui cedolini. */
    DATA_CONTRATTUALE,
    /** Dedotto dai cedolini caricati (primo/ultimo cedolino, valori stampati). */
    CEDOLINI,
    /** Calcolato dalle regole del CCNL. */
    CCNL,
    /** Non determinabile con i dati disponibili. */
    NON_DISPONIBILE
}
