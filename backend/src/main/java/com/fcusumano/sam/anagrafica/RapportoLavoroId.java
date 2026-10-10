package com.fcusumano.sam.anagrafica;

/**
 * Identificativo generato dal sistema per l'entita' RapportoLavoro.
 */
public record RapportoLavoroId(long valore) {

    public RapportoLavoroId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id non valido: " + valore);
        }
    }
}
