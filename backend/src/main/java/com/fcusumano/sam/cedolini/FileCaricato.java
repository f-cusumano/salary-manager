package com.fcusumano.sam.cedolini;

/**
 * File PDF ricevuto dall'upload.
 *
 * @param nomeFileOriginale nome del file come caricato (solo per la UI: il nome salvato e' normalizzato)
 * @param contenuto         byte del PDF
 */
public record FileCaricato(String nomeFileOriginale, byte[] contenuto) {

    public FileCaricato {
        if (contenuto == null || contenuto.length == 0) {
            throw new IllegalArgumentException("File vuoto: " + nomeFileOriginale);
        }
    }
}
