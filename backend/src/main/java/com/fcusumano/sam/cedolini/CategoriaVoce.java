package com.fcusumano.sam.cedolini;

/**
 * Categoria di una voce del cedolino, usata per le aree tematiche del dettaglio.
 */
public enum CategoriaVoce {
    /** Competenza retributiva (paga base, straordinari, premi, ratei 13a/14a, ferie liquidate, ...). */
    COMPETENZA,
    /** Contributi a carico del dipendente (INPS, fondi, ...). */
    TRATTENUTA_PREVIDENZIALE,
    /** IRPEF, addizionali, conguagli fiscali, detrazioni. */
    TRATTENUTA_FISCALE,
    /** Altre trattenute (sindacali, cessione del quinto, recuperi anticipi, ...). */
    ALTRA_TRATTENUTA,
    /** Voci che entrano nel netto ma non nella retribuzione (rimborsi km, diarie, rimborsi spese, buoni pasto). */
    NON_RETRIBUTIVA,
    /** Presenze/assenze (giorni lavorati, malattia, donazione sangue, ...), senza importo o informative. */
    PRESENZA,
    /** Altre voci informative senza effetto sul netto. */
    INFORMATIVA
}
