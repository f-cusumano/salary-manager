package com.fcusumano.sam.cedolini.application.estrazione;

/**
 * Il file caricato non e' un PDF utilizzabile per l'estrazione: non e' un PDF, e' danneggiato, e' protetto da una
 * password sconosciuta o supera i limiti dell'API. E' un {@link EstrazioneCedolinoException} con motivo
 * {@link Motivo#DOCUMENTO_NON_VALIDO}: il messaggio e' pensato per l'utente e non contiene dati personali.
 */
public class PdfNonLeggibileException extends EstrazioneCedolinoException {

    /** Messaggio mostrato quando nessuna password candidata apre il PDF. */
    public static final String MESSAGGIO_PASSWORD = "PDF protetto da password: indica la password e ricarica";

    /** Causa specifica. */
    public enum Causa {
        /** Il contenuto non e' un PDF (intestazione %PDF- assente). */
        NON_PDF,
        /** PDF danneggiato o senza pagine. */
        DANNEGGIATO,
        /** PDF protetto da password di apertura e nessuna password candidata funziona. */
        PROTETTO_DA_PASSWORD,
        /** PDF troppo grande per una richiesta all'API. */
        TROPPO_GRANDE,
        /** PDF con piu' pagine di quante l'API ne accetti. */
        TROPPE_PAGINE
    }

    private final Causa causa;

    public PdfNonLeggibileException(Causa causa, String messaggio) {
        super(Motivo.DOCUMENTO_NON_VALIDO, messaggio);
        this.causa = causa;
    }

    public PdfNonLeggibileException(Causa causa, String messaggio, Throwable origine) {
        super(Motivo.DOCUMENTO_NON_VALIDO, messaggio, origine);
        this.causa = causa;
    }

    public Causa causa() {
        return causa;
    }
}
