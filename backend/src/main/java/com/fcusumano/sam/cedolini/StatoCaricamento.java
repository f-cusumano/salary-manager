package com.fcusumano.sam.cedolini;

/**
 * Stato dell'elaborazione di un file caricato.
 */
public enum StatoCaricamento {
    IN_CODA,
    IN_LETTURA,
    /** Cedolino salvato. */
    ACQUISITO,
    /** Cedolino gia' presente (stesso file o stessa competenza con contenuto equivalente): scartato. */
    DUPLICATO,
    /** Stessa competenza di un cedolino presente ma contenuto diverso (es. aggiuntivo premi/arretrati): scartato. */
    AGGIUNTIVO_NON_GESTITO,
    /** Errore (estrazione fallita, PDF con piu' cedolini, dati obbligatori mancanti, ...): vedi messaggio e motivo. */
    ERRORE;

    public boolean terminale() {
        return this != IN_CODA && this != IN_LETTURA;
    }
}
