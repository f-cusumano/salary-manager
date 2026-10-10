package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * PROGRESSIVI_IN_CALO (avviso): progressivi annui (imponibile previdenziale, imponibile IRPEF, IRPEF trattenuta) in
 * calo, oltre la tolleranza, tra due cedolini consecutivi dello stesso anno. Il confronto riparte a gennaio.
 * <p>
 * Si confrontano solo mensilita' ordinarie e cedolini di fine rapporto: tredicesima e quattordicesima separate
 * possono essere elaborate prima della mensilita' che le precede nell'ordine cronologico (ad esempio la tredicesima
 * elaborata tra novembre e dicembre) e il confronto darebbe falsi cali.
 * <p>
 * Imponibile IRPEF e IRPEF trattenuta possono calare legittimamente con un conguaglio fiscale: il calo non si segnala
 * se il cedolino piu' recente e' di dicembre, del mese di cessazione, di fine rapporto o riporta voci di conguaglio o
 * rimborso IRPEF (es. 730). L'imponibile previdenziale non e' toccato dai conguagli fiscali.
 */
public final class RegolaProgressiviInCalo implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.PROGRESSIVI_IN_CALO.name();

    private static final Pattern VOCE_CONGUAGLIO =
            Pattern.compile("(?i)congu|\\b730\\b|rimb\\w*\\.?\\s*irpef|irpef\\s*rimb|credito\\s*irpef");

    private final BigDecimal tolleranza;

    /**
     * @param tolleranza calo minimo (euro) considerato
     */
    public RegolaProgressiviInCalo(BigDecimal tolleranza) {
        this.tolleranza = Objects.requireNonNull(tolleranza, "tolleranza");
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var confrontabili = contesto.cedolini().stream()
                .filter(c -> c.tipo() == TipoMensilita.ORDINARIA || c.tipo() == TipoMensilita.FINE_RAPPORTO)
                .toList();
        var risultato = new ArrayList<SegnalazioneRilevata>();
        for (int i = 1; i < confrontabili.size(); i++) {
            var precedente = confrontabili.get(i - 1);
            var corrente = confrontabili.get(i);
            if (ContestoRapporto.periodo(precedente).getYear() != ContestoRapporto.periodo(corrente).getYear()) {
                continue;
            }
            boolean conguaglio = conguaglioFiscale(contesto, corrente);
            var cali = new ArrayList<Calo>();
            aggiungiSeInCalo(cali, "imponibile previdenziale", ProgressiviAnnui::imponibilePrevidenziale, precedente,
                    corrente);
            if (!conguaglio) {
                aggiungiSeInCalo(cali, "imponibile IRPEF", ProgressiviAnnui::imponibileIrpef, precedente, corrente);
                aggiungiSeInCalo(cali, "IRPEF trattenuta", ProgressiviAnnui::irpefTrattenuta, precedente, corrente);
            }
            if (!cali.isEmpty()) {
                risultato.add(segnalazione(precedente, corrente, cali));
            }
        }
        return risultato;
    }

    private void aggiungiSeInCalo(List<Calo> cali, String nome, Function<ProgressiviAnnui, BigDecimal> campo,
                                  CedolinoNormalizzato precedente, CedolinoNormalizzato corrente) {
        var prima = campo.apply(precedente.progressivi());
        var dopo = campo.apply(corrente.progressivi());
        if (Attivita.inCalo(prima, dopo, tolleranza)) {
            cali.add(new Calo(nome, prima, dopo));
        }
    }

    /** Il cedolino puo' contenere un conguaglio fiscale che giustifica il calo dei progressivi IRPEF. */
    private static boolean conguaglioFiscale(ContestoRapporto contesto, CedolinoNormalizzato cedolino) {
        if (cedolino.tipo() == TipoMensilita.FINE_RAPPORTO) {
            return true;
        }
        var periodo = ContestoRapporto.periodo(cedolino);
        if (periodo.getMonthValue() == 12) {
            return true;
        }
        var cessazione = contesto.dataCessazione();
        if (cessazione != null && YearMonth.from(cessazione).equals(periodo)
                || cedolino.dataCessazione() != null && YearMonth.from(cedolino.dataCessazione()).equals(periodo)) {
            return true;
        }
        return cedolino.voci().stream()
                .anyMatch(v -> v.descrizione() != null && VOCE_CONGUAGLIO.matcher(v.descrizione()).find());
    }

    private SegnalazioneRilevata segnalazione(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente,
                                              List<Calo> cali) {
        var dettagli = new ArrayList<String>();
        var evidenze = new ArrayList<Evidenza>();
        for (var calo : cali) {
            dettagli.add(calo.nome() + " da " + Formato.euro(calo.prima()) + " a " + Formato.euro(calo.dopo()));
            evidenze.add(Evidenze.importo("Progressivo " + calo.nome(), precedente, calo.prima()));
            evidenze.add(Evidenze.importo("Progressivo " + calo.nome(), corrente, calo.dopo()));
        }
        var descrizione = "Nel cedolino di " + Formato.competenza(corrente.competenza())
                + " i progressivi annui calano rispetto al cedolino di " + Formato.competenza(precedente.competenza())
                + ": " + String.join("; ", dettagli) + ". Nel corso dello stesso anno i progressivi dovrebbero solo "
                + "crescere.";
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.PROGRESSIVI_IN_CALO, Gravita.AVVISO, corrente.id(),
                null, "Progressivi annui in calo: " + Formato.competenza(corrente.competenza()), descrizione, evidenze);
    }

    private record Calo(String nome, BigDecimal prima, BigDecimal dopo) {
    }
}
