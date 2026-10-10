package com.fcusumano.sam.cedolini.application;

/**
 * Il cedolino estratto non puo' essere archiviato (dati indispensabili non leggibili). Il messaggio e' mostrato
 * all'utente nello stato del caricamento: non contiene dati personali.
 */
class CedolinoNonAcquisibileException extends RuntimeException {

    CedolinoNonAcquisibileException(String messaggio) {
        super(messaggio);
    }
}
