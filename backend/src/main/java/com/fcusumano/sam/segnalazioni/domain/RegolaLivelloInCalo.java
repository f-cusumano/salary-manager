package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.util.ArrayList;
import java.util.List;

/**
 * LIVELLO_IN_CALO (anomalia): livello di inquadramento inferiore rispetto alla mensilita' ordinaria precedente.
 * Si valuta solo quando il verso della scala dei livelli e' certo per il CCNL del rapporto (vedi {@link ScalaLivelli}:
 * Commercio e Metalmeccanico industria); con CCNL non noto, scala non definita, livelli non riconosciuti, livelli di
 * classificazioni diverse o cambio del CCNL stampato non si segnala nulla.
 */
public final class RegolaLivelloInCalo implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.LIVELLO_IN_CALO.name();

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        if (contesto.ccnl() == null) {
            return List.of();
        }
        var scala = ScalaLivelli.per(contesto.ccnl().codice());
        if (scala.isEmpty()) {
            return List.of();
        }
        var ordinari = contesto.ordinari();
        var risultato = new ArrayList<SegnalazioneRilevata>();
        for (int i = 1; i < ordinari.size(); i++) {
            var precedente = ordinari.get(i - 1);
            var corrente = ordinari.get(i);
            if (Attivita.diversiEntrambiPresenti(precedente.ccnlTesto(), corrente.ccnlTesto())) {
                continue;
            }
            var gradoPrecedente = scala.get().grado(precedente.livello());
            var gradoCorrente = scala.get().grado(corrente.livello());
            if (gradoPrecedente.isEmpty() || gradoCorrente.isEmpty()
                    || !gradoPrecedente.get().classificazione().equals(gradoCorrente.get().classificazione())
                    || gradoCorrente.get().rango() >= gradoPrecedente.get().rango()) {
                continue;
            }
            var livelloPrecedente = precedente.livello().trim();
            var livelloCorrente = corrente.livello().trim();
            var descrizione = "Il livello di inquadramento passa da " + livelloPrecedente + " ("
                    + Formato.competenza(precedente.competenza()) + ") a " + livelloCorrente + " ("
                    + Formato.competenza(corrente.competenza()) + "): " + scala.get().verso()
                    + ". Un passaggio a un livello inferiore è insolito e va verificato.";
            var evidenze = List.of(
                    Evidenze.valore("Livello", precedente, livelloPrecedente),
                    Evidenze.valore("Livello", corrente, livelloCorrente));
            risultato.add(new SegnalazioneRilevata(CODICE, TipoSegnalazione.LIVELLO_IN_CALO, Gravita.ANOMALIA,
                    corrente.id(), null, "Livello di inquadramento in calo: " + Formato.competenza(corrente.competenza()),
                    descrizione, evidenze));
        }
        return risultato;
    }
}
