package com.fcusumano.sam.cedolini.application;

/**
 * Il cedolino estratto non puo' essere archiviato (dati indispensabili non leggibili). Il messaggio e' mostrato
 * all'utente nello stato del caricamento: non contiene dati personali.
 */
class CedolinoNonAcquisibileException extends RuntimeException {

    /** Dato indispensabile mancante: l'inserimento a mano lo riporta sul campo corrispondente del modulo. */
    enum Dato {
        PERIODO,
        CODICE_FISCALE,
        DATORE
    }

    private final Dato dato;

    CedolinoNonAcquisibileException(Dato dato, String messaggio) {
        super(messaggio);
        this.dato = dato;
    }

    Dato dato() {
        return dato;
    }
}
