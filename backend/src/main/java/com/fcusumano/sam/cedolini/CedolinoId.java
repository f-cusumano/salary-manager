package com.fcusumano.sam.cedolini;

/**
 * Identificativo generato dal sistema per l'entita' Cedolino.
 */
public record CedolinoId(long valore) {

    public CedolinoId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id non valido: " + valore);
        }
    }
}
