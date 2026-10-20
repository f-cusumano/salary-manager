package com.fcusumano.sam.calcoli.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.calcoli.StatoPreavviso;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.DurataPreavviso;
import com.fcusumano.sam.ccnl.RegolaPreavviso;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalcoloPreavvisoTest {

    private static final Ccnl COMMERCIO = new Ccnl("COMMERCIO", "Commercio", 14, 36, 10, 7, null);
    private static final String ART = "Art. 247 (licenziamento) e 254 (dimissioni)";
    // Commercio, 2° e 3° livello: 20/30/45 giorni per le dimissioni, 30/45/60 per il licenziamento
    // (in ordine sparso: il calcolo ordina per fascia)
    private static final List<RegolaPreavviso> REGOLE = List.of(
            regola(10, 45, 60),
            regola(0, 20, 30),
            regola(5, 30, 45),
            new RegolaPreavviso(Set.of("4", "5"), "4° e 5° livello", 0, new DurataPreavviso(0, 15),
                    new DurataPreavviso(0, 20), ART));
    private static final Retribuzione RETRIBUZIONE = new Retribuzione(new BigDecimal("28000.00"),
            new BigDecimal("2000.00"), 14, null, null, null, null, null, null, false, null, null);

    @Test
    void terminiDellaFasciaDiAnzianitaConIndennitaSostitutiva() {
        var preavviso = CalcoloPreavviso.calcola(COMMERCIO, " 3° Livello ", anzianita(2020, 1, 6, 2), RETRIBUZIONE,
                REGOLE);

        assertThat(preavviso.stato()).isEqualTo(StatoPreavviso.VERIFICATO);
        assertThat(preavviso.livello()).isEqualTo("3° Livello");
        assertThat(preavviso.gruppoLivelli()).isEqualTo("2° e 3° livello");
        assertThat(preavviso.fascia()).isEqualTo("oltre 5 e fino a 10 anni");
        assertThat(preavviso.prossimaFascia()).isEqualTo(YearMonth.of(2030, 1));
        assertThat(preavviso.dimissioni()).isEqualTo(new DurataPreavviso(0, 30));
        assertThat(preavviso.licenziamento()).isEqualTo(new DurataPreavviso(0, 45));
        // 2000 x 14 / 12 = 2333,33 al mese: 30 giorni = 1 mese, 45 giorni = 1,5 mesi
        assertThat(preavviso.indennitaDimissioni()).isEqualByComparingTo("2333.33");
        assertThat(preavviso.indennitaLicenziamento()).isEqualByComparingTo("3500.00");
        assertThat(preavviso.riferimento()).isEqualTo(ART);
        assertThat(preavviso.nota()).contains("2.000,00 €").contains("x 14 / 12");
    }

    @ParameterizedTest(name = "{0} anni e {1} mesi: {2}")
    @CsvSource(delimiter = ';', value = {
            "0;1;fino a 5 anni;20",
            "5;0;fino a 5 anni;20",
            "5;1;oltre 5 e fino a 10 anni;30",
            "10;0;oltre 5 e fino a 10 anni;30",
            "10;1;oltre 10 anni;45"})
    void laFasciaSuccessivaValeDalMeseDopoLaSoglia(int anni, int mesi, String fascia, int giorniDimissioni) {
        var preavviso = CalcoloPreavviso.calcola(COMMERCIO, "2", anzianita(2010, 1, anni, mesi), RETRIBUZIONE, REGOLE);

        assertThat(preavviso.fascia()).isEqualTo(fascia);
        assertThat(preavviso.dimissioni().giorni()).isEqualTo(giorniDimissioni);
    }

    @Test
    void nellUltimaFasciaNonCeUnaProssimaFascia() {
        var preavviso = CalcoloPreavviso.calcola(COMMERCIO, "3", anzianita(2010, 1, 12, 0), RETRIBUZIONE, REGOLE);

        assertThat(preavviso.prossimaFascia()).isNull();
    }

    @Test
    void conUnaSolaFasciaQualunqueAnzianita() {
        var preavviso = CalcoloPreavviso.calcola(COMMERCIO, "4", anzianita(2010, 1, 12, 0), RETRIBUZIONE, REGOLE);

        assertThat(preavviso.fascia()).isEqualTo("qualunque anzianità");
        assertThat(preavviso.indennitaLicenziamento()).isEqualByComparingTo("1555.56");
    }

    @Test
    void senzaRetribuzioneITerminiCiSonoMaLIndennitaNo() {
        var senza = new Retribuzione(null, null, null, null, null, null, null, null, null, false, null, null);
        var preavviso = CalcoloPreavviso.calcola(COMMERCIO, "3", anzianita(2020, 1, 1, 0), senza, REGOLE);

        assertThat(preavviso.stato()).isEqualTo(StatoPreavviso.VERIFICATO);
        assertThat(preavviso.dimissioni()).isEqualTo(new DurataPreavviso(0, 20));
        assertThat(preavviso.indennitaDimissioni()).isNull();
        assertThat(preavviso.indennitaLicenziamento()).isNull();
        assertThat(preavviso.nota()).startsWith("Retribuzione non determinabile");
    }

    @Test
    void motiviPerCuiIlPreavvisoNonEDeterminabile() {
        var anzianita = anzianita(2020, 1, 1, 0);

        assertThat(CalcoloPreavviso.calcola(null, "3", anzianita, RETRIBUZIONE, List.of()).stato())
                .isEqualTo(StatoPreavviso.CCNL_DA_INDICARE);
        var nonVerificato = CalcoloPreavviso.calcola(COMMERCIO, "3", anzianita, RETRIBUZIONE, List.of());
        assertThat(nonVerificato.stato()).isEqualTo(StatoPreavviso.CCNL_NON_VERIFICATO);
        assertThat(nonVerificato.nota()).contains("Commercio");
        assertThat(nonVerificato.dimissioni()).isNull();
        assertThat(CalcoloPreavviso.calcola(COMMERCIO, null, anzianita, RETRIBUZIONE, REGOLE).stato())
                .isEqualTo(StatoPreavviso.LIVELLO_NON_INDICATO);
        var sconosciuto = CalcoloPreavviso.calcola(COMMERCIO, "Quadro", anzianita, RETRIBUZIONE, REGOLE);
        assertThat(sconosciuto.stato()).isEqualTo(StatoPreavviso.LIVELLO_NON_RICONOSCIUTO);
        assertThat(sconosciuto.nota()).contains("\"Quadro\"");
        var senzaAnzianita = new Anzianita(null, null, true, 0, 0, FonteDato.NON_DISPONIBILE, FonteDato.NON_DISPONIBILE);
        assertThat(CalcoloPreavviso.calcola(COMMERCIO, "3", senzaAnzianita, RETRIBUZIONE, REGOLE).stato())
                .isEqualTo(StatoPreavviso.ANZIANITA_NON_DETERMINABILE);
    }

    @Test
    void indennitaPerMesiEGiorni() {
        // 1 mese e 15 giorni su 2.600 al mese con 13 mensilita': 2.816,67 x 1,5
        assertThat(CalcoloPreavviso.indennita(new BigDecimal("2600"), 13, new DurataPreavviso(1, 15)))
                .isEqualByComparingTo("4225.00");
    }

    private static RegolaPreavviso regola(int oltreAnni, int dimissioni, int licenziamento) {
        return new RegolaPreavviso(Set.of("2", "3"), "2° e 3° livello", oltreAnni, new DurataPreavviso(0, dimissioni),
                new DurataPreavviso(0, licenziamento), ART);
    }

    /** Anzianita' dal mese indicato, lunga anni e mesi (estremi compresi). */
    private static Anzianita anzianita(int anno, int mese, int anni, int mesi) {
        var dal = YearMonth.of(anno, mese);
        return new Anzianita(dal, dal.plusMonths(anni * 12L + mesi - 1), true, anni, mesi, FonteDato.DATA_CONTRATTUALE,
                FonteDato.CEDOLINI);
    }
}
