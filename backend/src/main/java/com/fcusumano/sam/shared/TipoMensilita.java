package com.fcusumano.sam.shared;

/**
 * Tipo di cedolino rispetto alla competenza.
 */
public enum TipoMensilita {
    /** Mensilita' ordinaria, mesi 1..12. */
    ORDINARIA,
    /** Tredicesima, mese convenzionale 13. */
    TREDICESIMA,
    /** Quattordicesima, mese convenzionale 14. */
    QUATTORDICESIMA,
    /** Cedolino successivo alla cessazione del rapporto (liquidazione): progressivo TFR01, TFR02, ... */
    FINE_RAPPORTO
}
