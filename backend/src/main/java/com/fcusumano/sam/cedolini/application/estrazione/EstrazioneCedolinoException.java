package com.fcusumano.sam.cedolini.application.estrazione;

/**
 * Estrazione non riuscita. Il messaggio e' mostrato all'utente nello stato del caricamento: non deve contenere dati
 * personali.
 */
public class EstrazioneCedolinoException extends RuntimeException {

    /** Causa dell'errore di estrazione. */
    public enum Motivo {
        /** Il modello ha rifiutato la richiesta (stop_reason = refusal). */
        RIFIUTO,
        /** Risposta troncata per limite di token (stop_reason = max_tokens). */
        LIMITE_TOKEN,
        /** Output non conforme allo schema o non interpretabile. */
        OUTPUT_NON_VALIDO,
        /** Il documento non e' un cedolino oppure contiene piu' cedolini distinti. */
        DOCUMENTO_NON_VALIDO,
        /** Errore di rete, timeout, rate limit o errore del servizio. */
        SERVIZIO_NON_DISPONIBILE,
        /** Configurazione mancante (es. ANTHROPIC_API_KEY non impostata). */
        CONFIGURAZIONE,
        /**
         * Il PDF non contiene testo leggibile (scansione o immagine): il lettore locale non lo puo' interpretare, il
         * lettore Claude si.
         */
        PDF_SENZA_TESTO,
        /**
         * Il lettore locale non riconosce il layout del cedolino, o lo riconosce ma non ne trova i dati essenziali
         * (periodo, codice fiscale, netto): meglio un errore che un dato sbagliato.
         */
        LAYOUT_NON_RICONOSCIUTO
    }

    private final Motivo motivo;

    public EstrazioneCedolinoException(Motivo motivo, String messaggio) {
        super(messaggio);
        this.motivo = motivo;
    }

    public EstrazioneCedolinoException(Motivo motivo, String messaggio, Throwable causa) {
        super(messaggio, causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
