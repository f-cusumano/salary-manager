package com.fcusumano.sam.calcoli;

/**
 * Voce della liquidazione stimata, nell'ordine di presentazione.
 */
public enum TipoVoceLiquidazione {
    /** TFR accantonato in azienda (le quote versate ai fondi pensione non sono liquidate dall'azienda). */
    TFR,
    FERIE,
    PERMESSI,
    ROL,
    EX_FESTIVITA,
    BANCA_ORE,
    /** Ratei di tredicesima maturati e non ancora pagati. */
    RATEI_TREDICESIMA,
    /** Ratei di quattordicesima maturati e non ancora pagati. */
    RATEI_QUATTORDICESIMA
}
