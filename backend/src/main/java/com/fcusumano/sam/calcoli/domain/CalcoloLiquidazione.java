package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Liquidazione;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Ratei;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.SaldoRateo;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.VoceLiquidazione;
import com.fcusumano.sam.calcoli.TipoVoceLiquidazione;
import com.fcusumano.sam.calcoli.domain.ParametriLiquidazione.ScaglioniIrpef;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Stima della liquidazione ("Se andassi via oggi") alla data dell'ultimo cedolino ordinario, o alla cessazione se nota:
 * nessuna proiezione dopo l'ultimo cedolino. Sempre una stima. Non si applica se il TFR e' gia' liquidato.
 * <ul>
 *   <li><b>TFR in azienda</b> (dal calcolo TFR, non proiettato) con tassazione separata (art. 19 TUIR): reddito di
 *       riferimento = (TFR al netto delle rivalutazioni gia' assoggettate a imposta sostitutiva + quote versate ai
 *       fondi + anticipi) x 12 / mesi di anzianita'; aliquota media = IRPEF lorda del reddito di riferimento (scaglioni
 *       dell'anno della data di riferimento) / reddito di riferimento, applicata al TFR al netto delle rivalutazioni
 *       nette. Le rivalutazioni nette sono quelle degli anni con dati TFR sui cedolini; quelle degli anni senza dati
 *       restano imponibili (stima prudente). Nessun contributo.</li>
 *   <li><b>Ferie, permessi, ROL, ex festivita', banca ore</b>: saldi e valore stimato del riepilogo ratei (un'unita'
 *       per tipo: quella con il valore stimato); un saldo negativo e' un debito.</li>
 *   <li><b>Ratei di tredicesima</b>: mesi maturati dall'inizio dell'anno (o dall'assunzione, o dall'ultimo pagamento
 *       nell'anno) al mese della data di riferimento x lordo mensile / 12. Alla cessazione il mese conta se lavorato
 *       per almeno 15 giorni.</li>
 *   <li><b>Ratei di quattordicesima</b> (CCNL a 14 mensilita'): stessa regola su un periodo di 12 mesi che inizia nel
 *       mese di pagamento del CCNL (es. luglio: da luglio a giugno). Non stimabile se il mese non e' noto.</li>
 *   <li>Ferie e ratei: contributi INPS a carico del dipendente (aliquota stampata sul cedolino, altrimenti da
 *       configurazione) e IRPEF all'aliquota marginale stimata sulla RAL al netto dei contributi, senza addizionali,
 *       detrazioni e conguagli.</li>
 * </ul>
 */
public final class CalcoloLiquidazione {

    static final String NOTA_GENERALE = "Stima senza proiezioni dopo l'ultimo cedolino, con il preavviso considerato "
            + "lavorato. Non comprende straordinari, premi e altre voci variabili.";
    static final String NOTA_FONDI =
            "Le quote di TFR versate ai fondi pensione (%s) non sono comprese: le liquida il fondo.";
    static final String NOTA_CONTRIBUTI_STAMPATI = "Contributi INPS al %s, come stampato sul cedolino.";
    static final String NOTA_CONTRIBUTI_DEFAULT = "Contributi INPS al %s (aliquota non stampata sul cedolino).";
    static final String NOTA_IRPEF = "IRPEF all'aliquota marginale del %s stimata sulla RAL al netto dei contributi, "
            + "senza addizionali, detrazioni e conguaglio di fine anno.";
    static final String NOTA_IRPEF_ASSENTE = "RAL non determinabile: IRPEF su ferie e ratei non stimabile.";

    static final String NOTA_TFR_NON_DETERMINABILE = "TFR in azienda non determinabile.";
    static final String NOTA_TFR_TASSAZIONE = "Tassazione separata: aliquota media del %s sul reddito di riferimento "
            + "di %s (%s x 12 / %d mesi di anzianità), con gli scaglioni IRPEF %d.";
    static final String NOTA_TFR_RIVALUTAZIONI =
            "Rivalutazioni nette già tassate con l'imposta sostitutiva (%s) escluse dalla tassazione.";
    static final String NOTA_TFR_ANNI_SENZA_DATI =
            "Le rivalutazioni degli anni senza dati TFR sui cedolini sono considerate imponibili.";
    static final String NOTA_TFR_ANTICIPI = "Gli anticipi già erogati (%s) concorrono solo all'aliquota.";
    static final String NOTA_TFR_RIFERIMENTO = "TFR dell'ultimo cedolino con dati TFR (%s).";
    static final String NOTA_TFR_ALIQUOTA_ASSENTE = "Anzianità non determinabile: imposta non stimabile.";

    static final String NOTA_RATEO_NON_STIMABILE =
            "Valore non stimabile: paga giornaliera/oraria e lordo mensile non disponibili.";
    static final String NOTA_RATEO = "Saldo dell'ultimo cedolino che riporta i ratei.";
    static final String NOTA_TREDICESIMA = "%d/12 del lordo mensile (%s) per i mesi da %s a %s.";
    static final String NOTA_QUATTORDICESIMA = "%d/12 del lordo mensile (%s) per i mesi da %s a %s (periodo di "
            + "maturazione da %s a %s).";
    static final String NOTA_LORDO_ASSENTE = "Lordo mensile non determinabile: ratei non stimabili.";
    static final String NOTA_MESE_QUATTORDICESIMA_IGNOTO =
            "Il mese di pagamento della quattordicesima del CCNL non è noto: ratei non stimabili.";

    /** Voci previdenziali INPS (IVS, FPLD, CIGS, FIS, FAP) di cui sommare l'aliquota stampata. */
    private static final Pattern VOCE_INPS =
            Pattern.compile("(?i)INPS|\\bIVS\\b|F\\.?P\\.?L\\.?D|\\bCIGS\\b|\\bFIS\\b|\\bFAP\\b");
    private static final BigDecimal CENTO = BigDecimal.valueOf(100);
    private static final BigDecimal DODICI = BigDecimal.valueOf(12);
    private static final BigDecimal ALIQUOTA_INPS_MIN = new BigDecimal("0.05");
    private static final BigDecimal ALIQUOTA_INPS_MAX = new BigDecimal("0.15");
    private static final int GIORNO_MESE_INTERO = 15;
    private static final int SCALA = 10;

    private CalcoloLiquidazione() {
    }

    public static Liquidazione calcola(ContestoRapporto contesto, CedoliniOrdinati cedolini, Anzianita anzianita,
                                       Retribuzione retribuzione, Ratei ratei, Tfr tfr, ParametriCalcolo parametri,
                                       ParametriLiquidazione parametriLiquidazione) {
        var ultimo = cedolini.ultimoOrdinario(c -> true);
        if (tfr.liquidato() || ultimo.isEmpty()) {
            return Liquidazione.NESSUNA;
        }
        var cessazione = contesto.dataCessazione();
        var data = cessazione != null ? cessazione : CedoliniOrdinati.mese(ultimo.get()).atEndOfMonth();
        var ultimoMese = cessazione == null ? CedoliniOrdinati.mese(ultimo.get())
                : cessazione.getDayOfMonth() >= GIORNO_MESE_INTERO ? YearMonth.from(cessazione)
                : YearMonth.from(cessazione).minusMonths(1);
        var scaglioni = parametriLiquidazione.scaglioni(data.getYear());

        var contributi = aliquotaContributi(ultimo.get())
                .map(a -> new Aliquota(a, true))
                .orElseGet(() -> new Aliquota(parametriLiquidazione.aliquotaContributiDefault(), false));
        var imponibileAnnuo = retribuzione.ral() == null ? null
                : retribuzione.ral().multiply(BigDecimal.ONE.subtract(contributi.valore()));
        var marginale = imponibileAnnuo == null ? null : scaglioni.aliquotaMarginale(imponibileAnnuo);
        var tassazione = new TassazioneOrdinaria(contributi.valore(), marginale);

        var voci = new ArrayList<VoceLiquidazione>();
        var tfrVoce = tfr(cedolini, anzianita, tfr, parametri, scaglioni, data.getYear());
        voci.add(tfrVoce.voce());
        voci.addAll(ratei(ratei, tassazione));
        tredicesima(cedolini, anzianita, retribuzione, ultimoMese, tassazione).ifPresent(voci::add);
        quattordicesima(contesto, cedolini, anzianita, retribuzione, ultimoMese, tassazione).ifPresent(voci::add);

        var note = new ArrayList<String>();
        note.add(NOTA_GENERALE);
        if (positivo(tfr.quotaFondi())) {
            note.add(NOTA_FONDI.formatted(euro(tfr.quotaFondi())));
        }
        note.add((contributi.stampata() ? NOTA_CONTRIBUTI_STAMPATI : NOTA_CONTRIBUTI_DEFAULT)
                .formatted(percentuale(contributi.valore())));
        note.add(marginale == null ? NOTA_IRPEF_ASSENTE : NOTA_IRPEF.formatted(percentuale(marginale)));
        return new Liquidazione(
                data,
                voci,
                Importi.normalizza(contributi.valore()),
                contributi.stampata(),
                Importi.normalizza(marginale),
                Importi.normalizza(tfrVoce.aliquota()),
                String.join(" ", note));
    }

    // ------------------------------------------------------------------------------------------------ TFR

    private record VoceTfr(VoceLiquidazione voce, BigDecimal aliquota) {
    }

    private static VoceTfr tfr(CedoliniOrdinati cedolini, Anzianita anzianita, Tfr tfr, ParametriCalcolo parametri,
                               ScaglioniIrpef scaglioni, int anno) {
        var lordo = tfr.quotaInAzienda();
        if (lordo == null) {
            var nota = tfr.nota() == null ? NOTA_TFR_NON_DETERMINABILE : NOTA_TFR_NON_DETERMINABILE + " " + tfr.nota();
            return new VoceTfr(voce(TipoVoceLiquidazione.TFR, null, null, null, null, null, null, nota), null);
        }
        var note = new ArrayList<String>();
        var mesi = anzianita.dal() == null ? 0 : anzianita.anni() * 12 + anzianita.mesi();
        var rivalutazioni = rivalutazioniNette(cedolini, parametri);
        var rivalutazioniEscluse = rivalutazioni.importo().max(BigDecimal.ZERO).min(lordo);
        var imponibile = lordo.subtract(rivalutazioniEscluse);
        BigDecimal aliquota = null;
        BigDecimal imposte = null;
        if (mesi <= 0) {
            note.add(NOTA_TFR_ALIQUOTA_ASSENTE);
        } else {
            var base = imponibile.add(Importi.zeroSeNull(tfr.quotaFondi())).add(Importi.zeroSeNull(tfr.anticipi()));
            var reddito = base.multiply(DODICI).divide(BigDecimal.valueOf(mesi), SCALA, RoundingMode.HALF_UP);
            aliquota = reddito.signum() <= 0 ? BigDecimal.ZERO
                    : scaglioni.imposta(reddito).divide(reddito, SCALA, RoundingMode.HALF_UP);
            imposte = Importi.euro(imponibile.multiply(aliquota));
            note.add(NOTA_TFR_TASSAZIONE.formatted(percentuale(aliquota), euro(reddito), euro(base), mesi,
                    scaglioni.dalAnno() <= anno ? anno : scaglioni.dalAnno()));
            if (positivo(tfr.anticipi())) {
                note.add(NOTA_TFR_ANTICIPI.formatted(euro(tfr.anticipi())));
            }
        }
        if (rivalutazioniEscluse.signum() > 0) {
            note.add(NOTA_TFR_RIVALUTAZIONI.formatted(euro(rivalutazioniEscluse)));
        }
        if (rivalutazioni.anniSenzaDati()) {
            note.add(NOTA_TFR_ANNI_SENZA_DATI);
        }
        var ultimoOrdinario = cedolini.ultimoOrdinario(c -> true).map(CedoliniOrdinati::mese).orElse(null);
        if (tfr.riferimento() != null && ultimoOrdinario != null && tfr.riferimento().isBefore(ultimoOrdinario)) {
            note.add(NOTA_TFR_RIFERIMENTO.formatted(Periodi.descrizione(tfr.riferimento())));
        }
        if (tfr.stimato() && tfr.nota() != null) {
            note.add(tfr.nota());
        }
        var netto = imposte == null ? null : Importi.euro(lordo).subtract(imposte);
        return new VoceTfr(voce(TipoVoceLiquidazione.TFR, null, null, Importi.euro(lordo), BigDecimal.ZERO.setScale(2),
                imposte, netto, String.join(" ", note)), aliquota);
    }

    private record Rivalutazioni(BigDecimal importo, boolean anniSenzaDati) {
    }

    /**
     * Rivalutazioni al netto dell'imposta sostitutiva negli anni con dati TFR: per anno quella della mensilita' di
     * dicembre se stampata, altrimenti l'ultima stampata; imposta stampata o rivalutazione x aliquota configurata.
     */
    private static Rivalutazioni rivalutazioniNette(CedoliniOrdinati cedolini, ParametriCalcolo parametri) {
        var perAnno = new TreeMap<Integer, List<CedolinoNormalizzato>>();
        cedolini.tutti().stream()
                .filter(c -> c.tfr() != null && c.tfr().rivalutazione() != null)
                .forEach(c -> perAnno.computeIfAbsent(c.competenza().anno(), a -> new ArrayList<>()).add(c));
        var totale = BigDecimal.ZERO;
        for (var entry : perAnno.entrySet()) {
            var anno = entry.getKey();
            var dellAnno = entry.getValue();
            var riferimento = dellAnno.stream()
                    .filter(c -> c.competenza().equals(new Competenza.Mensilita(anno, 12)))
                    .findFirst()
                    .orElse(dellAnno.getLast());
            DatiTfr dati = riferimento.tfr();
            var imposta = dati.impostaSostitutiva() != null ? dati.impostaSostitutiva()
                    : dati.rivalutazione().multiply(parametri.impostaSostitutivaRivalutazione());
            totale = totale.add(dati.rivalutazione().subtract(imposta).max(BigDecimal.ZERO));
        }
        var anniConDati = cedolini.tutti().stream()
                .filter(c -> CalcoloTfr.conDati(c.tfr()))
                .map(c -> c.competenza().anno())
                .distinct()
                .count();
        var primo = cedolini.tutti().stream().map(c -> c.competenza().anno()).min(Integer::compare).orElse(0);
        var ultimo = cedolini.tutti().stream().map(c -> c.competenza().anno()).max(Integer::compare).orElse(0);
        // il fondo al 31/12 del primo anno caricato contiene le rivalutazioni degli anni precedenti, mai viste
        var fondoIniziale = cedolini.tutti().stream()
                .filter(c -> c.competenza().anno() == primo && c.tfr() != null)
                .map(c -> c.tfr().fondo31DicembreAnnoPrecedente())
                .anyMatch(CalcoloLiquidazione::positivo);
        return new Rivalutazioni(totale, fondoIniziale || anniConDati < ultimo - primo + 1);
    }

    // ------------------------------------------------------------------------------------------------ ratei

    /** Ferie e permessi residui, un'unita' per tipo (quella con il valore stimato), nell'ordine delle voci. */
    private static List<VoceLiquidazione> ratei(Ratei ratei, TassazioneOrdinaria tassazione) {
        var voci = new ArrayList<VoceLiquidazione>();
        for (var tipo : TipoRateo.values()) {
            var saldi = ratei.saldi().stream().filter(s -> s.tipo() == tipo && s.saldo() != null).toList();
            var scelto = saldi.stream().filter(s -> s.valoreStimato() != null).findFirst()
                    .or(() -> saldi.stream().findFirst());
            if (scelto.isEmpty() || scelto.get().saldo().signum() == 0) {
                continue;
            }
            voci.add(rateo(scelto.get(), tassazione));
        }
        return voci;
    }

    private static VoceLiquidazione rateo(SaldoRateo saldo, TassazioneOrdinaria tassazione) {
        var tipo = TipoVoceLiquidazione.valueOf(saldo.tipo().name());
        if (saldo.valoreStimato() == null) {
            return voce(tipo, saldo.saldo(), saldo.unita(), null, null, null, null, NOTA_RATEO_NON_STIMABILE);
        }
        var nota = saldo.nota() == null ? NOTA_RATEO : NOTA_RATEO + " " + saldo.nota();
        return tassazione.voce(tipo, saldo.saldo(), saldo.unita(), saldo.valoreStimato(), nota);
    }

    // ------------------------------------------------------------------------------------------------ mensilita'

    private static Optional<VoceLiquidazione> tredicesima(CedoliniOrdinati cedolini, Anzianita anzianita,
                                                          Retribuzione retribuzione, YearMonth ultimoMese,
                                                          TassazioneOrdinaria tassazione) {
        var anno = ultimoMese.getYear();
        var pagata = cedolini.tutti().stream()
                .filter(c -> c.competenza().anno() == anno)
                .filter(c -> c.tipo() == TipoMensilita.TREDICESIMA || c.contieneTredicesima())
                .map(c -> c.tipo() == TipoMensilita.TREDICESIMA ? YearMonth.of(anno, 12) : CedoliniOrdinati.mese(c))
                .max(Comparator.naturalOrder());
        var inizio = inizio(YearMonth.of(anno, 1), anzianita, pagata);
        return mensilitaAggiuntiva(TipoVoceLiquidazione.RATEI_TREDICESIMA, inizio, ultimoMese, retribuzione,
                tassazione, (mesi, lordo) -> NOTA_TREDICESIMA.formatted(mesi, lordo, Periodi.descrizione(inizio),
                        Periodi.descrizione(ultimoMese)));
    }

    private static Optional<VoceLiquidazione> quattordicesima(ContestoRapporto contesto, CedoliniOrdinati cedolini,
                                                              Anzianita anzianita, Retribuzione retribuzione,
                                                              YearMonth ultimoMese, TassazioneOrdinaria tassazione) {
        var ccnl = contesto.ccnl();
        if (ccnl == null || !ccnl.prevedeQuattordicesima()) {
            return Optional.empty();
        }
        var mesePagamento = ccnl.meseQuattordicesima();
        if (mesePagamento == null || mesePagamento < 1 || mesePagamento > 12) {
            return Optional.of(voce(TipoVoceLiquidazione.RATEI_QUATTORDICESIMA, null, null, null, null, null, null,
                    NOTA_MESE_QUATTORDICESIMA_IGNOTO));
        }
        var periodo = ultimoMese.getMonthValue() >= mesePagamento
                ? YearMonth.of(ultimoMese.getYear(), mesePagamento)
                : YearMonth.of(ultimoMese.getYear() - 1, mesePagamento);
        // il cedolino del mese di pagamento paga il periodo precedente: contano solo i pagamenti successivi
        var pagata = cedolini.tutti().stream()
                .filter(CedolinoNormalizzato::contieneQuattordicesima)
                .map(CedoliniOrdinati::mese)
                .filter(m -> m.isAfter(periodo))
                .max(Comparator.naturalOrder());
        var inizio = inizio(periodo, anzianita, pagata);
        return mensilitaAggiuntiva(TipoVoceLiquidazione.RATEI_QUATTORDICESIMA, inizio, ultimoMese, retribuzione,
                tassazione, (mesi, lordo) -> NOTA_QUATTORDICESIMA.formatted(mesi, lordo, Periodi.descrizione(inizio),
                        Periodi.descrizione(ultimoMese), Periodi.descrizione(periodo),
                        Periodi.descrizione(periodo.plusMonths(11))));
    }

    /** Primo mese da pagare: inizio del periodo, spostato all'assunzione e dopo l'ultimo pagamento. */
    private static YearMonth inizio(YearMonth periodo, Anzianita anzianita, Optional<YearMonth> pagata) {
        var inizio = periodo;
        if (anzianita.dal() != null && anzianita.dal().isAfter(inizio)) {
            inizio = anzianita.dal();
        }
        if (pagata.isPresent() && !pagata.get().isBefore(inizio)) {
            inizio = pagata.get().plusMonths(1);
        }
        return inizio;
    }

    private interface NotaMensilita {
        String nota(long mesi, String lordoMensile);
    }

    private static Optional<VoceLiquidazione> mensilitaAggiuntiva(TipoVoceLiquidazione tipo, YearMonth inizio,
                                                                  YearMonth ultimoMese, Retribuzione retribuzione,
                                                                  TassazioneOrdinaria tassazione, NotaMensilita nota) {
        var mesi = Math.min(12, ChronoUnit.MONTHS.between(inizio, ultimoMese) + 1);
        if (mesi <= 0) {
            return Optional.empty();
        }
        var quantita = BigDecimal.valueOf(mesi);
        var lordoMensile = retribuzione.lordoMensile();
        if (lordoMensile == null) {
            return Optional.of(voce(tipo, quantita, null, null, null, null, null, NOTA_LORDO_ASSENTE));
        }
        var lordo = lordoMensile.multiply(quantita).divide(DODICI, SCALA, RoundingMode.HALF_UP);
        return Optional.of(tassazione.voce(tipo, quantita, null, lordo, nota.nota(mesi, euro(lordoMensile))));
    }

    // ------------------------------------------------------------------------------------------------ trattenute

    private record Aliquota(BigDecimal valore, boolean stampata) {
    }

    /** Somma delle aliquote INPS stampate sulle trattenute previdenziali del cedolino, se plausibile. */
    static Optional<BigDecimal> aliquotaContributi(CedolinoNormalizzato cedolino) {
        var somma = cedolino.vociDi(CategoriaVoce.TRATTENUTA_PREVIDENZIALE).stream()
                .filter(v -> v.descrizione() != null && VOCE_INPS.matcher(v.descrizione()).find())
                .map(Voce::quantita)
                .filter(q -> q != null && q.signum() > 0 && q.compareTo(CENTO) < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(CENTO, SCALA, RoundingMode.HALF_UP);
        return somma.compareTo(ALIQUOTA_INPS_MIN) >= 0 && somma.compareTo(ALIQUOTA_INPS_MAX) <= 0
                ? Optional.of(somma.stripTrailingZeros()) : Optional.empty();
    }

    /** Contributi e IRPEF marginale sulle voci di reddito ordinario (ferie, permessi, ratei). */
    private record TassazioneOrdinaria(BigDecimal aliquotaContributi, BigDecimal aliquotaIrpef) {

        VoceLiquidazione voce(TipoVoceLiquidazione tipo, BigDecimal quantita,
                              UnitaMisura unita, BigDecimal lordo, String nota) {
            var lordoEuro = Importi.euro(lordo);
            var contributi = Importi.euro(lordo.multiply(aliquotaContributi));
            var imposte = aliquotaIrpef == null ? null
                    : Importi.euro(lordo.subtract(lordo.multiply(aliquotaContributi)).multiply(aliquotaIrpef));
            var netto = imposte == null ? null : lordoEuro.subtract(contributi).subtract(imposte);
            return CalcoloLiquidazione.voce(tipo, quantita, unita, lordoEuro, contributi, imposte, netto, nota);
        }
    }

    // ------------------------------------------------------------------------------------------------ utilita'

    private static VoceLiquidazione voce(TipoVoceLiquidazione tipo, BigDecimal quantita,
                                         UnitaMisura unita, BigDecimal lordo,
                                         BigDecimal contributi, BigDecimal imposte, BigDecimal netto, String nota) {
        return new VoceLiquidazione(tipo, Importi.normalizza(quantita), unita, Importi.normalizza(lordo),
                Importi.normalizza(contributi), Importi.normalizza(imposte), Importi.normalizza(netto), nota);
    }

    private static boolean positivo(BigDecimal importo) {
        return importo != null && importo.signum() > 0;
    }

    static String percentuale(BigDecimal aliquota) {
        var formato = NumberFormat.getNumberInstance(Locale.ITALY);
        formato.setMinimumFractionDigits(0);
        formato.setMaximumFractionDigits(2);
        return formato.format(aliquota.movePointRight(2)) + "%";
    }

    static String euro(BigDecimal importo) {
        var formato = NumberFormat.getNumberInstance(Locale.ITALY);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return formato.format(importo.setScale(2, RoundingMode.HALF_UP)) + " €";
    }
}
