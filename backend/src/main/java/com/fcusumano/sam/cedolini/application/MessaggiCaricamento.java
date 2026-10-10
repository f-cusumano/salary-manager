package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.domain.DescrizioneCompetenza;
import com.fcusumano.sam.shared.Competenza;

/**
 * Messaggi dei caricamenti mostrati all'utente: comprensibili, senza dettagli tecnici ne' dati personali (al piu' il
 * periodo di competenza).
 */
final class MessaggiCaricamento {

    static final String FILE_GIA_CARICATO = "Lo stesso file è già stato caricato: scartato";
    /** Prima elaborazione interrotta dal riavvio: il PDF non e' conservato, il file va caricato di nuovo. */
    static final String INTERROTTO_DAL_RIAVVIO =
            "Elaborazione interrotta dal riavvio dell'applicazione: ricarica il file";
    /** Elaborazione interrotta dal riavvio di un file con il PDF conservato (es. una nuova lettura). */
    static final String INTERROTTO_DAL_RIAVVIO_RILEGGIBILE =
            "Elaborazione interrotta dal riavvio dell'applicazione: riprova la lettura o inserisci i dati a mano";
    /** Elaborazione interrotta con il PDF in memoria, che viene conservato. */
    static final String INTERROTTO = "Elaborazione interrotta: riprova la lettura o inserisci i dati a mano";
    static final String ERRORE_IMPREVISTO =
            "Errore imprevisto durante l'elaborazione del cedolino: riprova o contatta l'assistenza";
    static final String PERIODO_NON_LEGGIBILE = "Periodo di retribuzione (mese e anno) non leggibile sul cedolino";
    static final String CODICE_FISCALE_NON_LEGGIBILE = "Codice fiscale del dipendente non leggibile sul cedolino";
    static final String DATORE_NON_LEGGIBILE = "Ragione sociale del datore di lavoro non leggibile sul cedolino";
    static final String NON_IN_ERRORE =
            "Il file non è più in errore: è già stato inserito, rimosso o ricaricato";
    static final String IN_ELABORAZIONE = "Il file è in elaborazione: attendi la fine";
    static final String PDF_NON_CONSERVATO = "PDF del file non conservato: caricalo di nuovo";
    /**
     * Inserimento a mano rifiutato dal database perche' un valore (o un dato calcolato da importi e quantita', come la
     * RAL o il valore dei ratei) non sta nelle colonne.
     */
    static final String VALORI_TROPPO_GRANDI =
            "Alcuni importi o quantità sono troppo grandi: controlla i valori inseriti";

    // inserimento a mano: anagrafiche scelte dall'elenco o nuove ("Crea nuovo")

    static final String PERSONA_NON_IN_ARCHIVIO =
            "La persona scelta non è più in archivio: scegline un'altra o creane una nuova";
    static final String AZIENDA_NON_IN_ARCHIVIO =
            "L'azienda scelta non è più in archivio: scegline un'altra o creane una nuova";
    static final String CODICE_FISCALE_PERSONA_IN_ARCHIVIO =
            "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco";
    static final String PARTITA_IVA_IN_ARCHIVIO =
            "Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco";
    static final String CODICE_FISCALE_AZIENDA_IN_ARCHIVIO =
            "Questo codice fiscale è già in archivio: seleziona l'azienda dall'elenco";
    static final String POSIZIONE_INPS_IN_ARCHIVIO =
            "Questa posizione INPS è già in archivio: seleziona l'azienda dall'elenco";
    static final String RAGIONE_SOCIALE_IN_ARCHIVIO =
            "Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco";

    private MessaggiCaricamento() {
    }

    static String duplicato(Competenza competenza) {
        return "Cedolino di " + DescrizioneCompetenza.di(competenza) + " già presente con gli stessi importi: scartato";
    }

    static String aggiuntivoConImportiDiversi(Competenza competenza) {
        return "Per " + DescrizioneCompetenza.di(competenza) + " è già presente un cedolino con importi diversi: "
                + "cedolino aggiuntivo (es. premi o arretrati pagati a parte) non gestito";
    }

    static String aggiuntivo(Competenza competenza) {
        return "Cedolino aggiuntivo di " + DescrizioneCompetenza.di(competenza)
                + " (es. premi o arretrati pagati a parte): non gestito";
    }

    static String nonAcquisibile(String dettaglio) {
        return "Cedolino non acquisibile: " + dettaglio;
    }

    static String competenzaGiaPresente(Competenza competenza) {
        return "Per " + DescrizioneCompetenza.di(competenza) + " è già presente un cedolino";
    }

    static String competenzaGiaAssociata(Competenza competenza) {
        return DescrizioneCompetenza.di(competenza) + " è già associato a un altro file in errore";
    }
}
