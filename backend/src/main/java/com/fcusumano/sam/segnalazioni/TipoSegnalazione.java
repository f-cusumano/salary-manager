package com.fcusumano.sam.segnalazioni;

/**
 * Tipi di segnalazione (uno per regola).
 */
public enum TipoSegnalazione {
    /** Mensilita' ordinaria mancante tra la prima e l'ultima del rapporto (considerando assunzione/cessazione a meta' mese). */
    MENSILITA_MANCANTE,
    /** Tredicesima mancante per un anno concluso (esclusi i ratei liquidati nel cedolino di fine rapporto). */
    TREDICESIMA_MANCANTE,
    /** Quattordicesima mancante per un anno concluso, se prevista dal CCNL. */
    QUATTORDICESIMA_MANCANTE,
    /** Cedolino di fine rapporto mancante pur in presenza di data di cessazione. */
    FINE_RAPPORTO_MANCANTE,
    /**
     * Mensilita' con un file caricato ma non letto associato dall'utente (avviso): prende il posto della mensilita'
     * mancante.
     */
    CEDOLINO_IN_ERRORE,
    /** Fondo TFR in calo senza cause note (anticipo, trasferimento a fondo, liquidazione). */
    FONDO_TFR_IN_CALO,
    /** Numero di scatti di anzianita' in calo. */
    SCATTI_IN_CALO,
    /** Progressivi annui in calo nello stesso anno. */
    PROGRESSIVI_IN_CALO,
    /** Livello di inquadramento in calo. */
    LIVELLO_IN_CALO,
    /** Fondo TFR al 31/12 incoerente con fondo precedente, rivalutazione netta, quote e anticipi. */
    TFR_INCOERENTE,
    /** Scatto di anzianita' atteso (secondo CCNL o data stampata) ma non presente in busta. */
    SCATTO_NON_IN_BUSTA,
    /** CCNL non determinato: alcuni calcoli e controlli non sono possibili (informativa). */
    CCNL_NON_DETERMINATO
}
