package com.fcusumano.sam.cedolini.domain;

/**
 * Tipo di cedolino dichiarato dall'estrazione (cio' che il documento stampa), prima di applicare le regole di
 * competenza.
 */
public enum TipoDichiarato {
    ORDINARIA,
    TREDICESIMA,
    QUATTORDICESIMA,
    FINE_RAPPORTO,
    /** Cedolino aggiuntivo nello stesso mese (premi, arretrati): non gestito in fase 1. */
    AGGIUNTIVA
}
