package com.fcusumano.sam.web.error;

/**
 * File caricato oltre la dimensione massima consentita (HTTP 413).
 */
public class FileTroppoGrandeException extends RuntimeException {

    public FileTroppoGrandeException(String messaggio) {
        super(messaggio);
    }
}
