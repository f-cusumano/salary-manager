package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.domain.ContestoRapporto.FileInErrore;
import com.fcusumano.sam.shared.Competenza;
import java.util.ArrayList;
import java.util.List;

/**
 * CEDOLINO_IN_ERRORE (avviso): mensilita' senza cedolino a cui l'utente ha associato un file caricato ma non letto.
 * Prende il posto della mensilita' mancante (le regole di mensilita', tredicesima e quattordicesima mancanti ignorano
 * le mensilita' associate): il cedolino e' stato caricato e va solo inserito a mano o letto di nuovo se il PDF e'
 * conservato, altrimenti caricato di nuovo (il testo indica le azioni effettivamente possibili). Una segnalazione per
 * mensilita', con la competenza valorizzata; nessuna se nel frattempo e' stato archiviato un cedolino della stessa
 * mensilita'. Il nome del file non compare mai nei testi.
 */
public final class RegolaCedolinoInErrore implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.CEDOLINO_IN_ERRORE.name();

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var risultato = new ArrayList<SegnalazioneRilevata>();
        contesto.mensilitaInErrore().forEach((competenza, file) -> {
            if (!contesto.esisteCompetenza(competenza.anno(), competenza.mese())) {
                risultato.add(segnalazione(competenza, file));
            }
        });
        return risultato;
    }

    private static SegnalazioneRilevata segnalazione(Competenza.Mensilita competenza, FileInErrore file) {
        var titolo = Formato.competenza(competenza);
        var motivo = file.motivo();
        var descrizione = "Il cedolino di " + titolo + " è stato caricato ma non è stato letto"
                + (motivo == null ? "" : " (" + motivo.descrizione() + ")") + ": " + cosaFare(file);
        var evidenze = new ArrayList<Evidenza>();
        if (motivo != null) {
            evidenze.add(Evidenze.rapporto("Motivo", motivo.descrizione()));
        }
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.CEDOLINO_IN_ERRORE, Gravita.AVVISO, null, competenza,
                "Cedolino caricato con errore: " + titolo, descrizione, evidenze);
    }

    /**
     * Le azioni possibili, come nell'elenco dei file in errore: con il PDF conservato inserimento a mano o nuova
     * lettura; senza, un nuovo caricamento (di una copia valida, o diviso un file per cedolino).
     */
    private static String cosaFare(FileInErrore file) {
        if (file.pdfDisponibile()) {
            return "inseriscilo a mano dall'elenco «Cedolini caricati con errore» o riprova la lettura.";
        }
        if (file.motivo() == null) {
            return "il PDF non è stato conservato, caricalo di nuovo.";
        }
        return switch (file.motivo()) {
            case PDF_NON_VALIDO -> "caricane una copia valida.";
            case PDF_TROPPE_PAGINE, PIU_CEDOLINI -> "dividi il PDF in un file per cedolino e caricali.";
            default -> "il PDF non è stato conservato, caricalo di nuovo.";
        };
    }
}
