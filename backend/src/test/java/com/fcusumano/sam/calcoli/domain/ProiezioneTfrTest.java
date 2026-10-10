package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ProiezioneTfrTest {

    private static final YearMonth DICEMBRE_2023 = YearMonth.of(2023, 12);

    /** TFR in corso calcolato sul cedolino di dicembre 2023 (in azienda 4526,32, quota mensile 160,60). */
    private static Tfr inCorso(String fondi, String nota) {
        return new Tfr(new BigDecimal("4526.32").add(new BigDecimal(fondi)), new BigDecimal("160.60"),
                new BigDecimal("4526.32"), new BigDecimal(fondi), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("3494.93"), new BigDecimal("0.23"), false, DICEMBRE_2023, nota, false, false, null, null);
    }

    @Test
    void proiettaIMesiTrascorsiConQuotaMensileERivalutazioneNetta() {
        var t = ProiezioneTfr.proietta(inCorso("0", null), YearMonth.of(2024, 4), PARAMETRI);

        // gennaio, febbraio e marzo: 3 x 160,60 + 4526,32 x 3% x 3/12 x (1 - 17%) = 481,80 + 28,18
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("5036.30");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("5036.30");
        // 3494,93 + 481,80 x 0,77 + 28,18
        assertThat(t.stimaNetto()).isEqualByComparingTo("3894.09");
        assertThat(t.stimato()).isTrue();
        assertThat(t.riferimento()).isEqualTo(DICEMBRE_2023);
        assertThat(t.nota()).isEqualTo("Stima ad oggi: 3 mesi proiettati dall'ultimo cedolino (quota mensile e "
                + "rivalutazione al tasso indicativo del 3% annuo, al netto dell'imposta sostitutiva).");
        assertThat(t.quotaMensile()).isEqualByComparingTo("160.60");
    }

    @Test
    void laNotaDellaProiezioneSiAggiungeAQuellaDelCalcolo() {
        var t = ProiezioneTfr.proietta(inCorso("0", CalcoloTfr.NOTA_NETTO_RIVALUTAZIONE), YearMonth.of(2024, 2),
                PARAMETRI);

        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_NETTO_RIVALUTAZIONE + " Stima ad oggi: 1 mese proiettato "
                + "dall'ultimo cedolino (quota mensile e rivalutazione al tasso indicativo del 3% annuo, al netto "
                + "dell'imposta sostitutiva).");
    }

    @Test
    void nessunaProiezioneSeIlMeseSuccessivoNonETrascorso() {
        var tfr = inCorso("0", null);

        assertThat(ProiezioneTfr.proietta(tfr, DICEMBRE_2023, PARAMETRI)).isSameAs(tfr);
        assertThat(ProiezioneTfr.proietta(tfr, YearMonth.of(2024, 1), PARAMETRI)).isSameAs(tfr);
    }

    @Test
    void nessunaProiezioneOltreIMesiMassimi() {
        var tfr = inCorso("0", null);

        assertThat(ProiezioneTfr.proietta(tfr, YearMonth.of(2025, 1), PARAMETRI).nota()).contains("12 mesi");
        assertThat(ProiezioneTfr.proietta(tfr, YearMonth.of(2025, 2), PARAMETRI)).isSameAs(tfr);
    }

    @Test
    void nessunaProiezioneConQuoteAiFondiTfrLiquidatoONonDeterminabile() {
        var conFondi = inCorso("300.00", null);
        var liquidato = new Tfr(BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, false, DICEMBRE_2023, "liquidato", false, true, new BigDecimal("5000"),
                null);
        var nonDeterminabile = new Tfr(null, new BigDecimal("160.60"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, false, DICEMBRE_2023, CalcoloTfr.NOTA_FONDO_ASSENTE, false, false, null,
                null);
        var oggi = YearMonth.of(2024, 4);

        assertThat(ProiezioneTfr.proietta(conFondi, oggi, PARAMETRI)).isSameAs(conFondi);
        assertThat(ProiezioneTfr.proietta(liquidato, oggi, PARAMETRI)).isSameAs(liquidato);
        assertThat(ProiezioneTfr.proietta(nonDeterminabile, oggi, PARAMETRI)).isSameAs(nonDeterminabile);
    }
}
