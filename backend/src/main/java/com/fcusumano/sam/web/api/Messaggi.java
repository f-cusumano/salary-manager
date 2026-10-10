package com.fcusumano.sam.web.api;

/**
 * Messaggi di validazione degli input REST (mostrati all'utente nei Problem Details).
 */
final class Messaggi {

    static final String ID_NON_VALIDO = "L'identificativo deve essere un numero intero positivo.";
    static final String LIMITE_NON_VALIDO = "Il limite deve essere compreso tra 1 e 200.";

    private Messaggi() {
    }
}
