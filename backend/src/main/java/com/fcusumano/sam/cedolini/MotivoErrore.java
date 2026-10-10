package com.fcusumano.sam.cedolini;

/**
 * Motivo per cui un caricamento e' finito in ERRORE (colonna {@code caricamento.motivo}). Determina le azioni possibili
 * sul file in errore: inserimento a mano e nuova lettura richiedono il PDF originale conservato, che si conserva solo
 * per i motivi con {@link #conservaPdf()}. Gli errori registrati prima dell'introduzione del motivo non ne hanno uno
 * (null) e sono trattati come un motivo senza PDF conservato.
 */
public enum MotivoErrore {
    /** PDF protetto da una password che nessuna password candidata apre. */
    PDF_PROTETTO(true, "Protetto da password"),
    /** PDF senza testo selezionabile (scansione o immagine): il lettore locale non lo interpreta. */
    PDF_SENZA_TESTO(true, "Scansione senza testo"),
    /** Layout non riconosciuto dal lettore locale, o riconosciuto ma senza i dati essenziali. */
    LAYOUT_NON_RICONOSCIUTO(true, "Layout non riconosciuto"),
    /** Lettura non riuscita: il documento non sembra un cedolino, risposta non valida, rifiuto o limite di token. */
    LETTURA_NON_RIUSCITA(true, "Lettura non riuscita"),
    /** Cedolino letto ma senza i dati indispensabili all'archiviazione (periodo, codice fiscale, datore di lavoro). */
    DATI_INCOMPLETI(true, "Dati essenziali mancanti"),
    /** PDF troppo grande per il lettore. */
    PDF_TROPPO_GRANDE(true, "PDF troppo grande per la lettura"),
    /** Lettore non disponibile (rete, servizio, configurazione). */
    SERVIZIO_NON_DISPONIBILE(true, "Lettore non disponibile"),
    /** Elaborazione interrotta (chiusura o riavvio dell'applicazione). */
    INTERROTTO(true, "Elaborazione interrotta"),
    /** Errore tecnico non previsto. */
    ERRORE_IMPREVISTO(true, "Errore imprevisto"),
    /** Il file non e' un PDF o e' danneggiato: non viene mai conservato ne' restituito. */
    PDF_NON_VALIDO(false, "File danneggiato o non PDF"),
    /** PDF con piu' pagine di quante il lettore ne accetti. */
    PDF_TROPPE_PAGINE(false, "Troppe pagine"),
    /** PDF con piu' cedolini distinti: va diviso in un file per cedolino. */
    PIU_CEDOLINI(false, "Più cedolini nello stesso PDF");

    private final boolean conservaPdf;
    private final String descrizione;

    MotivoErrore(boolean conservaPdf, String descrizione) {
        this.conservaPdf = conservaPdf;
        this.descrizione = descrizione;
    }

    /**
     * True se il PDF originale del file va conservato per completarlo a mano o rileggerlo (sempre che i byte siano
     * ancora in memoria: dopo un riavvio non lo sono).
     */
    public boolean conservaPdf() {
        return conservaPdf;
    }

    /** Etichetta breve in italiano, usata nei testi delle segnalazioni; mai dati personali. */
    public String descrizione() {
        return descrizione;
    }
}
