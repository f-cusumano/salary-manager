package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SCATTI_IN_CALO (anomalia): numero o importo mensile degli scatti di anzianita' in calo tra due mensilita' ordinarie
 * consecutive (tra quelle caricate). Gli scatti maturati non si perdono.
 * <p>
 * Esclusioni:
 * <ul>
 *   <li>cambio di livello (o livello non stampato su uno dei due cedolini: il cambio non si puo' escludere);</li>
 *   <li>cambio di CCNL stampato;</li>
 *   <li>per l'importo: calo contemporaneo del minimo o della retribuzione base (riduzione generale degli elementi, ad
 *       esempio passaggio a part-time), che non riguarda gli scatti in quanto tali.</li>
 * </ul>
 */
public final class RegolaScattiInCalo implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.SCATTI_IN_CALO.name();

    private final BigDecimal tolleranza;

    /**
     * @param tolleranza calo minimo (euro) dell'importo degli scatti considerato
     */
    public RegolaScattiInCalo(BigDecimal tolleranza) {
        this.tolleranza = Objects.requireNonNull(tolleranza, "tolleranza");
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var ordinari = contesto.ordinari();
        var risultato = new ArrayList<SegnalazioneRilevata>();
        for (int i = 1; i < ordinari.size(); i++) {
            var precedente = ordinari.get(i - 1);
            var corrente = ordinari.get(i);
            if (!stessoInquadramento(precedente, corrente)) {
                continue;
            }
            boolean numeroInCalo = precedente.numeroScatti() != null && corrente.numeroScatti() != null
                    && corrente.numeroScatti() < precedente.numeroScatti();
            var importoPrecedente = precedente.elementiRetributivi().importoScatti();
            var importoCorrente = corrente.elementiRetributivi().importoScatti();
            boolean importoInCalo = Attivita.inCalo(importoPrecedente, importoCorrente, tolleranza)
                    && !riduzioneGenerale(precedente, corrente);
            if (numeroInCalo || importoInCalo) {
                risultato.add(segnalazione(precedente, corrente, numeroInCalo, importoInCalo));
            }
        }
        return risultato;
    }

    /** Stesso livello stampato (presente su entrambi) e nessun cambio di CCNL stampato. */
    static boolean stessoInquadramento(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente) {
        var livelloPrecedente = Attivita.normalizza(precedente.livello());
        var livelloCorrente = Attivita.normalizza(corrente.livello());
        return livelloPrecedente != null && livelloPrecedente.equals(livelloCorrente)
                && !Attivita.diversiEntrambiPresenti(precedente.ccnlTesto(), corrente.ccnlTesto());
    }

    private boolean riduzioneGenerale(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente) {
        var prima = precedente.elementiRetributivi();
        var dopo = corrente.elementiRetributivi();
        return Attivita.inCalo(prima.minimo(), dopo.minimo(), tolleranza)
                || Attivita.inCalo(prima.retribuzioneBase(), dopo.retribuzioneBase(), tolleranza);
    }

    private SegnalazioneRilevata segnalazione(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente,
                                              boolean numeroInCalo, boolean importoInCalo) {
        var cali = new ArrayList<String>();
        var evidenze = new ArrayList<Evidenza>();
        if (numeroInCalo) {
            cali.add("il numero di scatti passa da " + precedente.numeroScatti() + " a " + corrente.numeroScatti());
            evidenze.add(Evidenze.valore("Numero scatti", precedente, String.valueOf(precedente.numeroScatti())));
            evidenze.add(Evidenze.valore("Numero scatti", corrente, String.valueOf(corrente.numeroScatti())));
        }
        if (importoInCalo) {
            var prima = precedente.elementiRetributivi().importoScatti();
            var dopo = corrente.elementiRetributivi().importoScatti();
            cali.add("l'importo mensile degli scatti passa da " + Formato.euro(prima) + " a " + Formato.euro(dopo));
            evidenze.add(Evidenze.importo("Importo scatti", precedente, prima));
            evidenze.add(Evidenze.importo("Importo scatti", corrente, dopo));
        }
        var descrizione = "Rispetto al cedolino di " + Formato.competenza(precedente.competenza()) + ", nel cedolino di "
                + Formato.competenza(corrente.competenza()) + " " + String.join(" e ", cali)
                + ", senza cambio di livello o di CCNL. Gli scatti di anzianità maturati non dovrebbero diminuire.";
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.SCATTI_IN_CALO, Gravita.ANOMALIA, corrente.id(), null,
                "Scatti di anzianità in calo: " + Formato.competenza(corrente.competenza()), descrizione, evidenze);
    }
}
