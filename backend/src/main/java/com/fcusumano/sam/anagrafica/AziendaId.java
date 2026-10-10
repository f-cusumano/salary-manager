package com.fcusumano.sam.anagrafica;

/**
 * Identificativo generato dal sistema per l'entita' Azienda.
 */
public record AziendaId(long valore) {

    public AziendaId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id non valido: " + valore);
        }
    }
}
