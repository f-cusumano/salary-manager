package com.fcusumano.sam.shared;

/**
 * Operazione non consentita dallo stato corrente dei dati (tradotta in HTTP 409 dal modulo web).
 * Il messaggio e' mostrato all'utente: non deve contenere dati personali.
 */
public class OperazioneNonConsentitaException extends RuntimeException {

    public OperazioneNonConsentitaException(String messaggio) {
        super(messaggio);
    }
}
