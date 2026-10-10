package com.fcusumano.sam.shared;

/**
 * Risorsa richiesta inesistente nel workspace corrente (tradotta in HTTP 404 dal modulo web).
 */
public class RisorsaNonTrovataException extends RuntimeException {

    private final String risorsa;
    private final Object id;

    public RisorsaNonTrovataException(String risorsa, Object id) {
        super(risorsa + " non trovato/a: " + id);
        this.risorsa = risorsa;
        this.id = id;
    }

    public String risorsa() {
        return risorsa;
    }

    public Object id() {
        return id;
    }
}
