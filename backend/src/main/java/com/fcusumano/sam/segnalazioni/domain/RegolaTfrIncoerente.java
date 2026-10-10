package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TFR_INCOERENTE (avviso): il fondo al 31/12/Y (stampato sui cedolini di Y+1) differisce, oltre la tolleranza, da
 * fondo al 31/12/Y-1 + rivalutazione - imposta sostitutiva + quota TFR dell'anno - quote a fondi - anticipi.
 * <p>
 * Si valuta solo se tutti i dati necessari sono presenti e univoci:
 * <ul>
 *   <li>fondo al 31/12/Y-1 e al 31/12/Y stampati; se coincidono il fondo non e' ancora stato aggiornato dal software
 *       e non si segnala;</li>
 *   <li>cedolino ordinario di dicembre Y con dati TFR; quota dell'anno = massimo progressivo stampato nell'anno (la
 *       tredicesima separata puo' essere elaborata prima o dopo dicembre);</li>
 *   <li>rivalutazione e imposta sostitutiva lette sull'ultimo cedolino dell'anno che le stampa, purche' di dicembre
 *       (obbligatorie se il fondo iniziale e' positivo);</li>
 *   <li>anticipi e quote a fondi interpretabili con certezza come importo annuo (vedi
 *       {@link StoricoTfr#importoAnnuo});</li>
 *   <li>nessuna liquidazione, cedolino di fine rapporto, cessazione o quota TFR in busta nell'anno.</li>
 * </ul>
 * Un calo del fondo gia' segnalato da FONDO_TFR_IN_CALO non viene segnalato di nuovo (una segnalazione per anomalia).
 */
public final class RegolaTfrIncoerente implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.TFR_INCOERENTE.name();

    private final BigDecimal tolleranza;
    private final BigDecimal tolleranzaCalo;

    /**
     * @param tolleranza     scostamento massimo (euro) tollerato tra fondo stampato e fondo atteso
     * @param tolleranzaCalo tolleranza della regola FONDO_TFR_IN_CALO, per non segnalare due volte lo stesso calo
     */
    public RegolaTfrIncoerente(BigDecimal tolleranza, BigDecimal tolleranzaCalo) {
        this.tolleranza = Objects.requireNonNull(tolleranza, "tolleranza");
        this.tolleranzaCalo = Objects.requireNonNull(tolleranzaCalo, "tolleranzaCalo");
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var storico = new StoricoTfr(contesto, tolleranzaCalo);
        return storico.anniConFondo().stream()
                .map(anno -> valutaAnno(contesto, storico, anno))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SegnalazioneRilevata> valutaAnno(ContestoRapporto contesto, StoricoTfr storico, int anno) {
        var iniziale = storico.fondoStampatoNel(anno);
        var finale = storico.fondoStampatoNel(anno + 1);
        if (iniziale.isEmpty() || finale.isEmpty() || iniziale.get().valore().signum() < 0) {
            return Optional.empty();
        }
        var fondoIniziale = iniziale.get().valore();
        var fondoFinale = finale.get().valore();
        if (fondoFinale.compareTo(fondoIniziale) == 0 || storico.caloSenzaCauseNote(anno).isPresent()
                || esclusoPerMovimenti(contesto, anno) || contesto.ordinario(YearMonth.of(anno, 12))
                .map(CedolinoNormalizzato::tfr).isEmpty()) {
            return Optional.empty();
        }
        var cedoliniAnno = storico.cedoliniTfr(anno);
        var quota = cedoliniAnno.stream().filter(c -> c.tfr().quotaAnno() != null)
                .max(Comparator.comparing(c -> c.tfr().quotaAnno()));
        // Rivalutazione: preferibilmente dal cedolino ordinario di dicembre, altrimenti dall'ultimo che la stampa
        var rivalutazione = contesto.ordinario(YearMonth.of(anno, 12))
                .filter(c -> c.tfr() != null && c.tfr().rivalutazione() != null)
                .or(() -> cedoliniAnno.stream().filter(c -> c.tfr().rivalutazione() != null).reduce((a, b) -> b));
        if (quota.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal importoRivalutazione = BigDecimal.ZERO;
        BigDecimal importoImposta = BigDecimal.ZERO;
        if (rivalutazione.isPresent()) {
            var tfr = rivalutazione.get().tfr();
            if (!ContestoRapporto.periodo(rivalutazione.get()).equals(YearMonth.of(anno, 12))
                    || Attivita.positivo(tfr.rivalutazione()) && tfr.impostaSostitutiva() == null) {
                return Optional.empty();
            }
            importoRivalutazione = tfr.rivalutazione();
            importoImposta = Objects.requireNonNullElse(tfr.impostaSostitutiva(), BigDecimal.ZERO);
        } else if (fondoIniziale.signum() != 0) {
            return Optional.empty();
        }
        var anticipi = storico.importoAnnuo(anno, DatiTfr::anticipi, true);
        var quoteFondi = storico.importoAnnuo(anno, DatiTfr::quotaFondi, false);
        if (anticipi.isEmpty() || quoteFondi.isEmpty()) {
            return Optional.empty();
        }
        var quotaAnno = quota.get().tfr().quotaAnno();
        var atteso = fondoIniziale.add(importoRivalutazione).subtract(importoImposta).add(quotaAnno)
                .subtract(quoteFondi.get()).subtract(anticipi.get());
        var scostamento = fondoFinale.subtract(atteso);
        if (scostamento.abs().compareTo(tolleranza) <= 0) {
            return Optional.empty();
        }
        var calcolo = new Calcolo(anno, iniziale.get(), finale.get(), rivalutazione.orElse(null), importoRivalutazione,
                importoImposta, quota.get(), quotaAnno, quoteFondi.get(), anticipi.get(), atteso, scostamento);
        return Optional.of(segnalazione(calcolo));
    }

    /** Movimenti che rendono il controllo non applicabile: liquidazioni, fine rapporto, cessazione, TFR in busta. */
    private static boolean esclusoPerMovimenti(ContestoRapporto contesto, int anno) {
        var cessazione = contesto.dataCessazione();
        if (cessazione != null && cessazione.getYear() == anno) {
            return true;
        }
        return contesto.dellAnno(anno).stream().anyMatch(c -> c.tipo() == TipoMensilita.FINE_RAPPORTO
                || Attivita.tfrLiquidato(c) || c.tfr() != null && Attivita.positivo(c.tfr().quotaBusta()));
    }

    private SegnalazioneRilevata segnalazione(Calcolo calcolo) {
        int anno = calcolo.anno();
        var formula = new StringBuilder()
                .append("fondo al ").append(Formato.fineAnno(anno - 1)).append(' ')
                .append(Formato.euro(calcolo.iniziale().valore()))
                .append(" + rivalutazione ").append(Formato.euro(calcolo.rivalutazione()))
                .append(" - imposta sostitutiva ").append(Formato.euro(calcolo.imposta()))
                .append(" + quota TFR del ").append(anno).append(' ').append(Formato.euro(calcolo.quotaAnno()));
        if (calcolo.quoteFondi().signum() != 0) {
            formula.append(" - quote a fondi pensione ").append(Formato.euro(calcolo.quoteFondi()));
        }
        if (calcolo.anticipi().signum() != 0) {
            formula.append(" - anticipi ").append(Formato.euro(calcolo.anticipi()));
        }
        var descrizione = "Il fondo TFR al " + Formato.fineAnno(anno) + " stampato ("
                + Formato.euro(calcolo.finale().valore()) + ") differisce di " + Formato.euro(calcolo.scostamento().abs())
                + " da quello atteso (" + Formato.euro(calcolo.atteso()) + " = " + formula + ").";

        var evidenze = new ArrayList<Evidenza>();
        evidenze.add(Evidenze.importo("Fondo TFR al " + Formato.fineAnno(anno - 1), calcolo.iniziale().cedolino(),
                calcolo.iniziale().valore()));
        if (calcolo.cedolinoRivalutazione() != null) {
            evidenze.add(Evidenze.importo("Rivalutazione", calcolo.cedolinoRivalutazione(), calcolo.rivalutazione()));
            evidenze.add(Evidenze.importo("Imposta sostitutiva", calcolo.cedolinoRivalutazione(), calcolo.imposta()));
        }
        evidenze.add(Evidenze.importo("Quota TFR del " + anno, calcolo.cedolinoQuota(), calcolo.quotaAnno()));
        if (calcolo.quoteFondi().signum() != 0) {
            evidenze.add(Evidenze.rapporto("Quote a fondi pensione del " + anno, Formato.euro(calcolo.quoteFondi())));
        }
        if (calcolo.anticipi().signum() != 0) {
            evidenze.add(Evidenze.rapporto("Anticipi TFR del " + anno, Formato.euro(calcolo.anticipi())));
        }
        evidenze.add(Evidenze.importo("Fondo TFR al " + Formato.fineAnno(anno), calcolo.finale().cedolino(),
                calcolo.finale().valore()));
        evidenze.add(Evidenze.rapporto("Fondo TFR atteso al " + Formato.fineAnno(anno), Formato.euro(calcolo.atteso())));
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.TFR_INCOERENTE, Gravita.AVVISO,
                calcolo.finale().cedolino().id(), null, "Fondo TFR al " + Formato.fineAnno(anno) + " incoerente",
                descrizione, evidenze);
    }

    private record Calcolo(
            int anno,
            StoricoTfr.ValoreStampato iniziale,
            StoricoTfr.ValoreStampato finale,
            CedolinoNormalizzato cedolinoRivalutazione,
            BigDecimal rivalutazione,
            BigDecimal imposta,
            CedolinoNormalizzato cedolinoQuota,
            BigDecimal quotaAnno,
            BigDecimal quoteFondi,
            BigDecimal anticipi,
            BigDecimal atteso,
            BigDecimal scostamento) {
    }
}
