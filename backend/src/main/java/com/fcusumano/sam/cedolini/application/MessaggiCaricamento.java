package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.domain.DescrizioneCompetenza;
import com.fcusumano.sam.shared.Competenza;

/**
 * Messaggi dei caricamenti mostrati all'utente: comprensibili, senza dettagli tecnici ne' dati personali (al piu' il
 * periodo di competenza).
 */
final class MessaggiCaricamento {

    static final String FILE_GIA_CARICATO = "Lo stesso file è già stato caricato: scartato";
    static final String INTERROTTO_DAL_RIAVVIO =
            "Elaborazione interrotta dal riavvio dell'applicazione: ricarica il file";
    static final String INTERROTTO = "Elaborazione interrotta: ricarica il file";
    static final String ERRORE_IMPREVISTO =
            "Errore imprevisto durante l'elaborazione del cedolino: riprova o contatta l'assistenza";
    static final String PERIODO_NON_LEGGIBILE = "Periodo di retribuzione (mese e anno) non leggibile sul cedolino";
    static final String CODICE_FISCALE_NON_LEGGIBILE = "Codice fiscale del dipendente non leggibile sul cedolino";
    static final String DATORE_NON_LEGGIBILE = "Ragione sociale del datore di lavoro non leggibile sul cedolino";
    static final String NON_IN_ERRORE =
            "Il file non è più in errore: è già stato inserito, rimosso o ricaricato";
    static final String IN_ELABORAZIONE = "Il file è in elaborazione: attendi la fine";
    static final String PDF_NON_CONSERVATO = "PDF del file non conservato: caricalo di nuovo";

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
