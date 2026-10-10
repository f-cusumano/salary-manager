package com.fcusumano.sam.web.error;

/**
 * Input della richiesta non valido (HTTP 400). Il messaggio e' mostrato all'utente: in italiano, senza dati personali
 * diversi da quelli inviati dall'utente stesso.
 */
public class RichiestaNonValidaException extends RuntimeException {

    public RichiestaNonValidaException(String messaggio) {
        super(messaggio);
    }
}
