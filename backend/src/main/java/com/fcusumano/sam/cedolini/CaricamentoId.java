package com.fcusumano.sam.cedolini;

/**
 * Identificativo generato dal sistema per l'entita' Caricamento.
 */
public record CaricamentoId(long valore) {

    public CaricamentoId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id non valido: " + valore);
        }
    }
}
