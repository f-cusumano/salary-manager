package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.util.ArrayList;
import java.util.List;

/**
 * CEDOLINO_IN_ERRORE (avviso): mensilita' senza cedolino a cui l'utente ha associato un file caricato ma non letto.
 * Prende il posto della mensilita' mancante (le regole di mensilita', tredicesima e quattordicesima mancanti ignorano
 * le mensilita' associate): il cedolino e' stato caricato e va solo inserito a mano o letto di nuovo. Una segnalazione
 * per mensilita', con la competenza valorizzata; nessuna se nel frattempo e' stato archiviato un cedolino della stessa
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
        contesto.mensilitaInErrore().forEach((competenza, motivo) -> {
            if (!contesto.esisteCompetenza(competenza.anno(), competenza.mese())) {
                risultato.add(segnalazione(competenza, motivo));
            }
        });
        return risultato;
    }

    private static SegnalazioneRilevata segnalazione(Competenza.Mensilita competenza, MotivoErrore motivo) {
        var titolo = Formato.competenza(competenza);
        var descrizione = "Il cedolino di " + titolo + " è stato caricato ma non è stato letto"
                + (motivo == null ? "" : " (" + motivo.descrizione() + ")")
                + ": inseriscilo a mano dall'elenco «Cedolini caricati con errore» o riprova la lettura.";
        var evidenze = new ArrayList<Evidenza>();
        if (motivo != null) {
            evidenze.add(Evidenze.rapporto("Motivo", motivo.descrizione()));
        }
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.CEDOLINO_IN_ERRORE, Gravita.AVVISO, null, competenza,
                "Cedolino caricato con errore: " + titolo, descrizione, evidenze);
    }
}
