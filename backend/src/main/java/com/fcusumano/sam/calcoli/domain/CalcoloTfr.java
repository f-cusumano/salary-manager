package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * TFR dai dati dell'anno dell'ultimo cedolino che riporta dati TFR (anno di riferimento), composti da tutti i cedolini
 * di quell'anno: un cedolino parziale (tredicesima separata, fine rapporto senza liquidazione) non nasconde i dati
 * stampati sugli altri.
 * <ul>
 *   <li>Dati dell'anno: fondo al 31/12 dell'anno precedente = ultimo valore stampato nell'anno; quota dell'anno =
 *       massimo valore stampato (e' progressiva e la tredicesima la incrementa); rivalutazione e imposta sostitutiva =
 *       quelle della mensilita' ordinaria di dicembre se ne stampa la rivalutazione, altrimenti l'ultimo valore
 *       stampato nell'anno.</li>
 *   <li>In azienda = fondo al 31/12 dell'anno precedente + rivalutazione - imposta sostitutiva + quota dell'anno
 *       - quote a fondi pensione dell'anno - quote in busta (QuIR) dell'anno - anticipi dell'anno. Fondo non stampato
 *       da nessun cedolino dell'anno: 0 solo se il rapporto e' iniziato (data di assunzione) nell'anno stesso,
 *       altrimenti non determinabile. Quota dell'anno assente: somma delle quote mensili dei cedolini dell'anno
 *       (stima). Imposta sostitutiva assente con rivalutazione stampata: rivalutazione x aliquota configurata
 *       (stima).</li>
 *   <li>Quote a fondi, in busta e anticipi di un anno: progressivo annuo stampato (ultimo valore); se la serie
 *       dell'anno cala, o se il valore coincide con la quota del mese mentre la quota dell'anno e' maggiore, sono
 *       importi del mese e si sommano (stima).</li>
 *   <li>Totale accumulato = in azienda + quote versate ai fondi negli anni coperti dai cedolini (senza i rendimenti
 *       del fondo, che il cedolino non riporta).</li>
 *   <li>Quota mensile: stampata sull'ultima mensilita' ordinaria con dati TFR, altrimenti retribuzione utile / divisore
 *       (13,5) marcata come stima.</li>
 *   <li>TFR liquidato da un cedolino di fine rapporto (o dal cedolino del mese di cessazione): accumulato 0, importo
 *       liquidato lordo e netto (lordo - ritenuta stampata), nessuna stima del netto.</li>
 *   <li>Stima del netto (sempre una stima): la tassazione separata (aliquota configurata) si applica alla quota in
 *       azienda al netto della rivalutazione netta dell'anno, gia' assoggettata a imposta sostitutiva (art. 19 TUIR);
 *       le rivalutazioni degli anni precedenti, comprese nel fondo, sono trattate come imponibili.</li>
 * </ul>
 * La proiezione "ad oggi" dei rapporti in corso e' applicata in lettura da {@link ProiezioneTfr}.
 */
public final class CalcoloTfr {

    static final String NOTA_NESSUN_DATO = "Nessun cedolino caricato riporta dati TFR.";
    static final String NOTA_LIQUIDATO = "TFR liquidato nel cedolino di fine rapporto di %s: nessun TFR residuo in azienda.";
    static final String NOTA_FONDO_ASSENTE =
            "Il fondo TFR al 31/12 dell'anno precedente non è stampato: TFR accumulato non determinabile.";
    static final String NOTA_INCOERENTE = "Dati TFR incoerenti (accumulato in azienda negativo): totale non determinabile.";
    static final String NOTA_QUOTA_ANNO_SOMMATA =
            "Quota TFR dell'anno non stampata: somma delle quote mensili dei cedolini caricati.";
    static final String NOTA_QUOTA_ANNO_ASSENTE = "Quota TFR dell'anno non stampata: non inclusa nel totale.";
    static final String NOTA_IMPOSTA_STIMATA =
            "Imposta sostitutiva sulla rivalutazione non stampata: stimata al %s%% della rivalutazione.";
    static final String NOTA_IMPORTI_MENSILI =
            "Quote a fondi/in busta o anticipi stampati come importi del mese: sommati sui cedolini dell'anno caricati.";
    static final String NOTA_FONDI = "Il totale comprende le quote versate ai fondi pensione risultanti dai cedolini "
            + "caricati, senza i rendimenti del fondo; la stima del netto riguarda solo la quota in azienda.";
    static final String NOTA_ANNI_MANCANTI = "Mancano i dati TFR di: %s; le relative quote ai fondi non sono incluse.";
    static final String NOTA_NETTO_RIVALUTAZIONE = "La stima del netto esclude dalla tassazione la rivalutazione netta "
            + "dell'anno; le rivalutazioni degli anni precedenti, comprese nel fondo, sono trattate come imponibili.";

    private static final int SCALA = 5;

    private CalcoloTfr() {
    }

    public static Tfr calcola(ContestoRapporto contesto, CedoliniOrdinati cedolini, Anzianita anzianita,
                              ParametriCalcolo parametri) {
        var conTfr = cedolini.tutti().stream().filter(c -> conDati(c.tfr())).toList();
        if (conTfr.isEmpty()) {
            return new Tfr(null, null, null, null, null, null, null, null, false, null, NOTA_NESSUN_DATO,
                    false, false, null, null);
        }
        var annuali = annuali(conTfr, parametri);
        var fondiTotali = somma(annuali.values().stream().map(QuoteAnno::fondi));
        var bustaTotale = somma(annuali.values().stream().map(QuoteAnno::busta));
        var anticipiTotali = somma(annuali.values().stream().map(QuoteAnno::anticipi));

        var liquidazioni = conTfr.stream().filter(c -> liquidazione(c, contesto)).toList();
        if (!liquidazioni.isEmpty()) {
            return liquidato(liquidazioni, fondiTotali, bustaTotale, anticipiTotali);
        }

        var riferimento = conTfr.getLast();
        var anno = riferimento.competenza().anno();
        var dellAnno = conTfr.stream().filter(c -> c.competenza().anno() == anno).toList();
        var note = new ArrayList<String>();
        var stimato = false;

        var quotaMensile = quotaMensile(cedolini, riferimento, parametri);

        var fondo = ultimoStampato(dellAnno, DatiTfr::fondo31DicembreAnnoPrecedente);
        if (fondo == null) {
            var iniziatoNellAnno = anzianita.fonteInizio() == FonteDato.DATA_CONTRATTUALE && anzianita.dal() != null
                    && anzianita.dal().getYear() == anno;
            if (!iniziatoNellAnno) {
                return nonDeterminabile(riferimento, quotaMensile, fondiTotali, bustaTotale, anticipiTotali,
                        NOTA_FONDO_ASSENTE);
            }
            fondo = BigDecimal.ZERO;
        }

        var quotaAnno = dellAnno.stream().map(c -> c.tfr().quotaAnno()).filter(Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);
        if (quotaAnno == null) {
            var quoteMese = dellAnno.stream()
                    .map(c -> c.tfr().quotaMese())
                    .filter(Objects::nonNull)
                    .toList();
            stimato = true;
            if (quoteMese.isEmpty()) {
                quotaAnno = BigDecimal.ZERO;
                note.add(NOTA_QUOTA_ANNO_ASSENTE);
            } else {
                quotaAnno = quoteMese.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                note.add(NOTA_QUOTA_ANNO_SOMMATA);
            }
        }

        var dicembre = dellAnno.stream()
                .filter(c -> c.competenza().equals(new Competenza.Mensilita(anno, 12)))
                .filter(c -> c.tfr().rivalutazione() != null)
                .findFirst();
        var rivalutazione = Importi.zeroSeNull(dicembre.map(c -> c.tfr().rivalutazione())
                .orElseGet(() -> ultimoStampato(dellAnno, DatiTfr::rivalutazione)));
        var imposta = dicembre.isPresent() ? dicembre.get().tfr().impostaSostitutiva()
                : ultimoStampato(dellAnno, DatiTfr::impostaSostitutiva);
        if (imposta == null) {
            imposta = BigDecimal.ZERO;
            if (rivalutazione.signum() > 0) {
                imposta = rivalutazione.multiply(parametri.impostaSostitutivaRivalutazione());
                stimato = true;
                note.add(NOTA_IMPOSTA_STIMATA.formatted(percentuale(parametri.impostaSostitutivaRivalutazione())));
            }
        }

        var quoteAnno = annuali.get(anno);
        if (annuali.values().stream().anyMatch(QuoteAnno::sommati)) {
            stimato = true;
            note.add(NOTA_IMPORTI_MENSILI);
        }
        var inAzienda = fondo.add(rivalutazione).subtract(imposta).add(quotaAnno)
                .subtract(quoteAnno.fondi()).subtract(quoteAnno.busta()).subtract(quoteAnno.anticipi());
        if (inAzienda.signum() < 0) {
            return nonDeterminabile(riferimento, quotaMensile, fondiTotali, bustaTotale, anticipiTotali,
                    NOTA_INCOERENTE);
        }

        if (fondiTotali.signum() > 0) {
            note.add(NOTA_FONDI);
            var mancanti = anniMancanti(annuali, anzianita, anno);
            if (!mancanti.isEmpty()) {
                stimato = true;
                note.add(NOTA_ANNI_MANCANTI.formatted(mancanti));
            }
        }

        var rivalutazioneNetta = rivalutazione.subtract(imposta).max(BigDecimal.ZERO).min(inAzienda);
        if (rivalutazioneNetta.signum() > 0) {
            note.add(NOTA_NETTO_RIVALUTAZIONE);
        }
        var stimaNetto = Importi.euro(inAzienda.subtract(rivalutazioneNetta)
                .multiply(BigDecimal.ONE.subtract(parametri.aliquotaTfr())).add(rivalutazioneNetta));
        return new Tfr(
                Importi.normalizza(inAzienda.add(fondiTotali)),
                Importi.normalizza(quotaMensile.importo()),
                Importi.normalizza(inAzienda),
                Importi.normalizza(fondiTotali),
                Importi.normalizza(bustaTotale),
                Importi.normalizza(anticipiTotali),
                Importi.normalizza(stimaNetto),
                Importi.normalizza(parametri.aliquotaTfr()),
                stimato,
                CedoliniOrdinati.mese(riferimento),
                note.isEmpty() ? null : String.join(" ", note),
                quotaMensile.stimata(),
                false,
                null,
                null);
    }

    // ------------------------------------------------------------------------------------------------ liquidazione

    /**
     * Cedolino che liquida il TFR: importo liquidato positivo su un cedolino di fine rapporto o su quello del mese di
     * cessazione (o successivo).
     */
    private static boolean liquidazione(CedolinoNormalizzato cedolino, ContestoRapporto contesto) {
        var liquidato = cedolino.tfr().liquidatoLordo();
        if (liquidato == null || liquidato.signum() <= 0) {
            return false;
        }
        return cedolino.tipo() == TipoMensilita.FINE_RAPPORTO
                || (contesto.dataCessazione() != null
                && !CedoliniOrdinati.mese(cedolino).isBefore(YearMonth.from(contesto.dataCessazione())));
    }

    private static Tfr liquidato(List<CedolinoNormalizzato> liquidazioni, BigDecimal fondi, BigDecimal busta,
                                 BigDecimal anticipi) {
        var lordo = somma(liquidazioni.stream().map(c -> c.tfr().liquidatoLordo()));
        var conRitenuta = liquidazioni.stream().allMatch(c -> c.tfr().ritenuta() != null);
        var netto = conRitenuta ? lordo.subtract(somma(liquidazioni.stream().map(c -> c.tfr().ritenuta()))) : null;
        var ultima = liquidazioni.getLast();
        var nota = NOTA_LIQUIDATO.formatted(Periodi.descrizione(CedoliniOrdinati.mese(ultima)));
        return new Tfr(
                Importi.normalizza(BigDecimal.ZERO),
                null,
                Importi.normalizza(BigDecimal.ZERO),
                Importi.normalizza(fondi),
                Importi.normalizza(busta),
                Importi.normalizza(anticipi),
                null,
                null,
                false,
                CedoliniOrdinati.mese(ultima),
                nota,
                false,
                true,
                Importi.normalizza(lordo),
                Importi.normalizza(netto));
    }

    private static Tfr nonDeterminabile(CedolinoNormalizzato riferimento, QuotaMensile quotaMensile, BigDecimal fondi,
                                        BigDecimal busta, BigDecimal anticipi, String nota) {
        return new Tfr(null, Importi.normalizza(quotaMensile.importo()), null, Importi.normalizza(fondi),
                Importi.normalizza(busta), Importi.normalizza(anticipi), null, null, false,
                CedoliniOrdinati.mese(riferimento), nota, quotaMensile.stimata(), false, null, null);
    }

    // ------------------------------------------------------------------------------------------------ quota mensile

    record QuotaMensile(BigDecimal importo, boolean stimata) {
    }

    /** Quota del mese dall'ultima mensilita' ordinaria con dati TFR (o dal riferimento se non ce ne sono). */
    static QuotaMensile quotaMensile(CedoliniOrdinati cedolini, CedolinoNormalizzato riferimento,
                                     ParametriCalcolo parametri) {
        var cedolino = cedolini.ultimoOrdinario(c -> conDati(c.tfr())).orElse(riferimento);
        var dati = cedolino.tfr();
        if (dati.quotaMese() != null) {
            return new QuotaMensile(dati.quotaMese(), false);
        }
        if (dati.retribuzioneUtile() != null && dati.retribuzioneUtile().signum() > 0) {
            return new QuotaMensile(
                    dati.retribuzioneUtile().divide(parametri.divisoreQuotaTfr(), SCALA, RoundingMode.HALF_UP), true);
        }
        return new QuotaMensile(null, false);
    }

    // ------------------------------------------------------------------------------------------------ quote annue

    /**
     * Quote a fondi, in busta e anticipi di un anno.
     *
     * @param sommati true se almeno una serie e' stata interpretata come importi del mese e sommata
     */
    record QuoteAnno(BigDecimal fondi, BigDecimal busta, BigDecimal anticipi, boolean sommati) {
    }

    private static Map<Integer, QuoteAnno> annuali(List<CedolinoNormalizzato> conTfr, ParametriCalcolo parametri) {
        var perAnno = new TreeMap<Integer, List<CedolinoNormalizzato>>();
        conTfr.forEach(c -> perAnno.computeIfAbsent(c.competenza().anno(), a -> new ArrayList<>()).add(c));
        var risultato = new TreeMap<Integer, QuoteAnno>();
        perAnno.forEach((anno, cedoliniAnno) -> {
            var fondi = serie(cedoliniAnno, DatiTfr::quotaFondi, true, parametri);
            var busta = serie(cedoliniAnno, DatiTfr::quotaBusta, true, parametri);
            var anticipi = serie(cedoliniAnno, DatiTfr::anticipi, false, parametri);
            risultato.put(anno, new QuoteAnno(fondi.importo(), busta.importo(), anticipi.importo(),
                    fondi.sommata() || busta.sommata() || anticipi.sommata()));
        });
        return risultato;
    }

    private record Serie(BigDecimal importo, boolean sommata) {
    }

    /**
     * Valore annuo di una serie stampata: ultimo valore se progressivo (serie non decrescente), somma dei valori se
     * importi del mese. Per le quote della quota TFR (fondi, busta) un valore pari alla quota del mese, quando la quota
     * dell'anno e' maggiore, indica un importo del mese.
     */
    private static Serie serie(List<CedolinoNormalizzato> cedoliniAnno, Function<DatiTfr, BigDecimal> campo,
                               boolean partiDellaQuota, ParametriCalcolo parametri) {
        var conValore = cedoliniAnno.stream().filter(c -> campo.apply(c.tfr()) != null).toList();
        if (conValore.isEmpty()) {
            return new Serie(BigDecimal.ZERO, false);
        }
        var valori = conValore.stream().map(c -> campo.apply(c.tfr())).toList();
        var nonDecrescente = true;
        for (int i = 1; i < valori.size(); i++) {
            if (valori.get(i).compareTo(valori.get(i - 1)) < 0) {
                nonDecrescente = false;
                break;
            }
        }
        var ultimo = conValore.getLast().tfr();
        var mensile = partiDellaQuota && ultimo.quotaMese() != null && ultimo.quotaAnno() != null
                && ultimo.quotaAnno().subtract(ultimo.quotaMese()).compareTo(parametri.tolleranzaAumento()) > 0
                && campo.apply(ultimo).subtract(ultimo.quotaMese()).compareTo(parametri.tolleranzaAumento()) <= 0;
        if (nonDecrescente && !mensile) {
            return new Serie(valori.getLast(), false);
        }
        return new Serie(somma(valori.stream()), valori.size() > 1);
    }

    /** Anni senza dati TFR tra l'inizio del rapporto (o il primo anno con dati) e l'anno di riferimento. */
    private static String anniMancanti(Map<Integer, QuoteAnno> annuali, Anzianita anzianita, int annoRiferimento) {
        var primoConDati = annuali.keySet().stream().min(Integer::compare).orElse(annoRiferimento);
        var primo = anzianita.dal() == null ? primoConDati : Math.min(anzianita.dal().getYear(), primoConDati);
        var intervalli = new ArrayList<String>();
        Integer inizio = null;
        for (int anno = primo; anno <= annoRiferimento + 1; anno++) {
            var manca = anno <= annoRiferimento && !annuali.containsKey(anno);
            if (manca && inizio == null) {
                inizio = anno;
            } else if (!manca && inizio != null) {
                intervalli.add(inizio == anno - 1 ? String.valueOf(inizio) : inizio + "-" + (anno - 1));
                inizio = null;
            }
        }
        return String.join(", ", intervalli);
    }

    // ------------------------------------------------------------------------------------------------ utilita'

    /** Ultimo valore stampato (in ordine cronologico) tra i cedolini indicati; null se nessuno lo stampa. */
    private static BigDecimal ultimoStampato(List<CedolinoNormalizzato> cedolini, Function<DatiTfr, BigDecimal> campo) {
        return cedolini.reversed().stream().map(c -> campo.apply(c.tfr())).filter(Objects::nonNull).findFirst()
                .orElse(null);
    }

    /** True se il cedolino riporta almeno un dato TFR. */
    static boolean conDati(DatiTfr tfr) {
        return tfr != null && Stream.of(tfr.fondo31DicembreAnnoPrecedente(), tfr.rivalutazione(),
                        tfr.impostaSostitutiva(), tfr.quotaAnno(), tfr.quotaMese(), tfr.quotaFondi(), tfr.quotaBusta(),
                        tfr.anticipi(), tfr.retribuzioneUtile(), tfr.liquidatoLordo(), tfr.ritenuta())
                .anyMatch(Objects::nonNull);
    }

    private static BigDecimal somma(Stream<BigDecimal> importi) {
        return importi.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String percentuale(BigDecimal aliquota) {
        return aliquota.movePointRight(2).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
