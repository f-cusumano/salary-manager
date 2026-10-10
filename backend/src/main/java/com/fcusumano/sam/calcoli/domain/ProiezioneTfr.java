package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import com.fcusumano.sam.shared.Importi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Stima "ad oggi" del TFR di un rapporto in corso: il TFR calcolato da {@link CalcoloTfr} e' fermo al mese
 * dell'ultimo cedolino con dati TFR; la proiezione aggiunge i mesi interamente trascorsi da allora fino al mese
 * precedente quello corrente (il mese in corso non e' ancora maturato).
 * <ul>
 *   <li>per ogni mese proiettato: quota mensile + rivalutazione pro rata della quota in azienda al tasso indicativo
 *       configurato, al netto dell'imposta sostitutiva;</li>
 *   <li>la stima del netto aggiunge le quote tassate all'aliquota stimata e la rivalutazione netta senza ulteriore
 *       tassazione (art. 19 TUIR);</li>
 *   <li>il risultato e' sempre marcato come stima, con una nota che indica i mesi proiettati.</li>
 * </ul>
 * Nessuna proiezione (TFR invariato) se il TFR e' liquidato o non determinabile, se manca la quota mensile, se parte
 * della quota va a fondi pensione o in busta (la ripartizione futura non e' nota), se non ci sono mesi trascorsi o se
 * ne sono trascorsi piu' del massimo configurato (cedolini troppo vecchi per una stima credibile). La proiezione e'
 * applicata in lettura: il riepilogo salvato resta deterministico (decisione 33).
 */
public final class ProiezioneTfr {

    static final String NOTA_PROIEZIONE = "Stima ad oggi: %d %s proiettat%s dall'ultimo cedolino (quota mensile e "
            + "rivalutazione al tasso indicativo del %s%% annuo, al netto dell'imposta sostitutiva).";

    private static final BigDecimal MESI_ANNO = BigDecimal.valueOf(12);
    private static final int SCALA = 10;

    private ProiezioneTfr() {
    }

    /**
     * TFR proiettato al mese corrente.
     *
     * @param tfr       TFR calcolato dai cedolini
     * @param meseOggi  mese corrente
     * @param parametri tasso di rivalutazione, imposta sostitutiva, aliquota e mesi massimi
     * @return il TFR proiettato, o lo stesso oggetto se la proiezione non si applica
     */
    public static Tfr proietta(Tfr tfr, YearMonth meseOggi, ParametriCalcolo parametri) {
        if (tfr.liquidato() || tfr.quotaInAzienda() == null || tfr.quotaMensile() == null
                || tfr.stimaNetto() == null || tfr.riferimento() == null
                || positivo(tfr.quotaFondi()) || positivo(tfr.quotaBusta())) {
            return tfr;
        }
        long mesi = ChronoUnit.MONTHS.between(tfr.riferimento(), meseOggi) - 1;
        if (mesi <= 0 || mesi > parametri.mesiMassimiProiezioneTfr()) {
            return tfr;
        }
        var n = BigDecimal.valueOf(mesi);
        var quote = tfr.quotaMensile().multiply(n);
        var rivalutazioneNetta = tfr.quotaInAzienda()
                .multiply(parametri.tassoRivalutazioneTfr())
                .multiply(n)
                .divide(MESI_ANNO, SCALA, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.subtract(parametri.impostaSostitutivaRivalutazione()));
        var aumento = quote.add(rivalutazioneNetta);
        var nettoAggiuntivo = quote.multiply(BigDecimal.ONE.subtract(parametri.aliquotaTfr())).add(rivalutazioneNetta);
        var nota = NOTA_PROIEZIONE.formatted(mesi, mesi == 1 ? "mese" : "mesi", mesi == 1 ? "o" : "i",
                percentuale(parametri.tassoRivalutazioneTfr()));
        return new Tfr(
                tfr.totaleAccumulato() == null ? null : Importi.euro(tfr.totaleAccumulato().add(aumento)),
                tfr.quotaMensile(),
                Importi.euro(tfr.quotaInAzienda().add(aumento)),
                tfr.quotaFondi(),
                tfr.quotaBusta(),
                tfr.anticipi(),
                Importi.euro(tfr.stimaNetto().add(nettoAggiuntivo)),
                tfr.aliquotaStimata(),
                true,
                tfr.riferimento(),
                tfr.nota() == null ? nota : tfr.nota() + " " + nota,
                tfr.quotaMensileStimata(),
                false,
                null,
                null);
    }

    private static boolean positivo(BigDecimal importo) {
        return importo != null && importo.signum() > 0;
    }

    private static String percentuale(BigDecimal tasso) {
        return tasso.movePointRight(2).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
