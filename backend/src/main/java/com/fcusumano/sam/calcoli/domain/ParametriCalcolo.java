package com.fcusumano.sam.calcoli.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parametri dei calcoli, letti dalla configurazione ({@code salary-manager.ferie.*}, {@code salary-manager.tfr.*},
 * {@code salary-manager.calcoli.*}): nessun valore e' cablato nel dominio.
 *
 * @param divisoreGiornaliero             lordo mensile / divisore = paga giornaliera, se non stampata (es. 26)
 * @param divisoreOrario                  lordo mensile / divisore = paga oraria, se non stampata (es. 173)
 * @param oreGiornaliere                  ore per giornata, per stimare i giorni quando e' nota solo la paga oraria
 * @param divisoreQuotaTfr                retribuzione utile / divisore = quota TFR del mese, se non stampata (13,5)
 * @param aliquotaTfr                     aliquota della tassazione separata per la stima del netto TFR
 * @param impostaSostitutivaRivalutazione aliquota dell'imposta sostitutiva, se la rivalutazione e' stampata senza imposta
 * @param tolleranzaAumento               variazione minima (euro) considerata un aumento negli indicatori
 * @param mensilitaSenzaCcnl              mensilita' assunte per la RAL quando il CCNL non e' indicato
 * @param cedoliniBuoniPasto              numero di ultimi cedolini ordinari esaminati per "Buoni pasto: SI"
 * @param tassoRivalutazioneTfr           tasso annuo indicativo di rivalutazione del TFR per la stima "ad oggi"
 * @param mesiMassimiProiezioneTfr        mesi massimi proiettati dall'ultimo cedolino nella stima "ad oggi" del TFR
 */
public record ParametriCalcolo(
        BigDecimal divisoreGiornaliero,
        BigDecimal divisoreOrario,
        BigDecimal oreGiornaliere,
        BigDecimal divisoreQuotaTfr,
        BigDecimal aliquotaTfr,
        BigDecimal impostaSostitutivaRivalutazione,
        BigDecimal tolleranzaAumento,
        int mensilitaSenzaCcnl,
        int cedoliniBuoniPasto,
        BigDecimal tassoRivalutazioneTfr,
        int mesiMassimiProiezioneTfr) {

    public ParametriCalcolo {
        positivo(divisoreGiornaliero, "divisoreGiornaliero");
        positivo(divisoreOrario, "divisoreOrario");
        positivo(oreGiornaliere, "oreGiornaliere");
        positivo(divisoreQuotaTfr, "divisoreQuotaTfr");
        traZeroEUno(aliquotaTfr, "aliquotaTfr");
        traZeroEUno(impostaSostitutivaRivalutazione, "impostaSostitutivaRivalutazione");
        Objects.requireNonNull(tolleranzaAumento, "tolleranzaAumento");
        if (tolleranzaAumento.signum() < 0) {
            throw new IllegalArgumentException("tolleranzaAumento non puo' essere negativa");
        }
        if (mensilitaSenzaCcnl < 12 || mensilitaSenzaCcnl > 14) {
            throw new IllegalArgumentException("mensilitaSenzaCcnl deve essere tra 12 e 14: " + mensilitaSenzaCcnl);
        }
        if (cedoliniBuoniPasto < 1) {
            throw new IllegalArgumentException("cedoliniBuoniPasto deve essere almeno 1: " + cedoliniBuoniPasto);
        }
        traZeroEUno(tassoRivalutazioneTfr, "tassoRivalutazioneTfr");
        if (mesiMassimiProiezioneTfr < 0) {
            throw new IllegalArgumentException(
                    "mesiMassimiProiezioneTfr non puo' essere negativo: " + mesiMassimiProiezioneTfr);
        }
    }

    private static void positivo(BigDecimal valore, String nome) {
        Objects.requireNonNull(valore, nome);
        if (valore.signum() <= 0) {
            throw new IllegalArgumentException(nome + " deve essere positivo: " + valore);
        }
    }

    private static void traZeroEUno(BigDecimal valore, String nome) {
        Objects.requireNonNull(valore, nome);
        if (valore.signum() < 0 || valore.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(nome + " deve essere tra 0 e 1: " + valore);
        }
    }
}
