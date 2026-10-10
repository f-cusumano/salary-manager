package com.fcusumano.sam.anagrafica;

/**
 * Origine di un estremo del periodo di un rapporto.
 */
public enum FontePeriodo {
    /** Data di assunzione/cessazione stampata sui cedolini. */
    DATA_CONTRATTUALE,
    /** Dedotto dal primo/ultimo cedolino caricato. */
    CEDOLINI,
    /** Non determinabile (nessun cedolino e nessuna data). */
    NON_DISPONIBILE
}
