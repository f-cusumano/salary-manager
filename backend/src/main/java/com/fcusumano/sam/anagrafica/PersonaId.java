package com.fcusumano.sam.anagrafica;

/**
 * Identificativo generato dal sistema per l'entita' Persona.
 */
public record PersonaId(long valore) {

    public PersonaId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id non valido: " + valore);
        }
    }
}
