package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.COMMERCIO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.METALMECCANICO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI_LIQUIDAZIONE;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.TELECOMUNICAZIONI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.datiTfr;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Liquidazione;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.VoceLiquidazione;
import com.fcusumano.sam.calcoli.TipoVoceLiquidazione;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CalcoloLiquidazioneTest {

    private static final OffsetDateTime ORA = OffsetDateTime.of(2026, 10, 20, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final LocalDate ASSUNZIONE = LocalDate.of(2020, 3, 1);

    private final CalcolatoreRiepilogo calcolatore = new CalcolatoreRiepilogo(PARAMETRI, PARAMETRI_LIQUIDAZIONE);

    /**
     * Rapporto inventato in corso: Commercio, 2.000 euro di base, cedolini da gennaio a ottobre 2025, TFR in azienda
     * 11.200 euro, 10 giorni di ferie residue, contributi IVS 9,19% stampati.
     */
    private static List<CedolinoNormalizzato> inCorso(int ultimoMese, Consumer<CedolinoDiProva> ultimo) {
        var cedolini = new ArrayList<CedolinoNormalizzato>();
        for (int mese = 1; mese <= ultimoMese; mese++) {
            var c = ordinario(2025, mese).date(ASSUNZIONE, null).base("2000.00")
                    .voce("Contributo IVS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, "9.19", "183.80")
                    .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "10")
                    .tfr(datiTfr("10000.00", null, null, String.valueOf(120 * mese), "120.00", null, null, null,
                            null, null, null));
            if (mese == ultimoMese) {
                ultimo.accept(c);
            }
            cedolini.add(c.build());
        }
        return cedolini;
    }

    private Liquidazione liquidazione(Ccnl ccnl, LocalDate cessazione, List<CedolinoNormalizzato> cedolini) {
        return calcolatore.calcola(contesto(ASSUNZIONE, cessazione, ccnl), cedolini, ORA).liquidazione();
    }

    private static VoceLiquidazione voce(Liquidazione l, TipoVoceLiquidazione tipo) {
        return l.voci().stream().filter(v -> v.tipo() == tipo).findFirst().orElseThrow();
    }

    private static void importi(VoceLiquidazione v, String lordo, String contributi, String imposte, String netto) {
        assertThat(v.lordo()).as("lordo %s", v.tipo()).isEqualByComparingTo(lordo);
        assertThat(v.contributi()).as("contributi %s", v.tipo()).isEqualByComparingTo(contributi);
        assertThat(v.imposte()).as("imposte %s", v.tipo()).isEqualByComparingTo(imposte);
        assertThat(v.netto()).as("netto %s", v.tipo()).isEqualByComparingTo(netto);
    }

    @Test
    void rapportoInCorsoAllaDataDellUltimoCedolino() {
        var l = liquidazione(COMMERCIO, null, inCorso(10, c -> { }));

        assertThat(l.presente()).isTrue();
        assertThat(l.dataRiferimento()).isEqualTo(LocalDate.of(2025, 10, 31));
        assertThat(l.voci()).extracting(VoceLiquidazione::tipo).containsExactly(TipoVoceLiquidazione.TFR,
                TipoVoceLiquidazione.FERIE, TipoVoceLiquidazione.RATEI_TREDICESIMA,
                TipoVoceLiquidazione.RATEI_QUATTORDICESIMA);
        assertThat(l.aliquotaContributi()).isEqualByComparingTo("0.0919");
        assertThat(l.aliquotaContributiStampata()).isTrue();
        // RAL 28.000 x (1 - 9,19%) = 25.426,80: primo scaglione 2025
        assertThat(l.aliquotaIrpefMarginale()).isEqualByComparingTo("0.23");

        // TFR 11.200, 68 mesi di anzianita': reddito di riferimento 1.976,47, aliquota media 23%
        var tfr = voce(l, TipoVoceLiquidazione.TFR);
        importi(tfr, "11200.00", "0", "2576.00", "8624.00");
        assertThat(l.aliquotaTfr()).isEqualByComparingTo("0.23");
        assertThat(tfr.nota()).contains("68 mesi").contains("scaglioni IRPEF 2025")
                .contains("rivalutazioni degli anni senza dati");

        // 10 giorni x 2.000 / 26
        var ferie = voce(l, TipoVoceLiquidazione.FERIE);
        assertThat(ferie.quantita()).isEqualByComparingTo("10");
        assertThat(ferie.unita()).isEqualTo(UnitaMisura.GIORNI);
        importi(ferie, "769.23", "70.69", "160.66", "537.88");

        // gennaio-ottobre: 10/12 di 2.000
        var tredicesima = voce(l, TipoVoceLiquidazione.RATEI_TREDICESIMA);
        assertThat(tredicesima.quantita()).isEqualByComparingTo("10");
        importi(tredicesima, "1666.67", "153.17", "348.11", "1165.39");

        // luglio-ottobre: 4/12 di 2.000 (periodo da luglio a giugno)
        var quattordicesima = voce(l, TipoVoceLiquidazione.RATEI_QUATTORDICESIMA);
        assertThat(quattordicesima.quantita()).isEqualByComparingTo("4");
        importi(quattordicesima, "666.67", "61.27", "139.24", "466.16");
        assertThat(quattordicesima.nota()).contains("da Luglio 2025 a Giugno 2026");

        assertThat(l.nota()).contains("preavviso").contains("9,19%").contains("23%");
    }

    @Test
    void tredicesimaPagataADicembreEQuattordicesimaDaGennaio() {
        var l = liquidazione(COMMERCIO, null, inCorso(12, CedolinoDiProva::conTredicesima));

        assertThat(l.voci()).extracting(VoceLiquidazione::tipo).doesNotContain(TipoVoceLiquidazione.RATEI_TREDICESIMA);
        assertThat(voce(l, TipoVoceLiquidazione.RATEI_QUATTORDICESIMA).quantita()).isEqualByComparingTo("6");
    }

    @Test
    void tredicesimaRiprendeDopoUnPagamentoNellAnno() {
        var cedolini = inCorso(5, c -> { });
        cedolini.set(2, ordinario(2025, 3).date(ASSUNZIONE, null).base("2000.00").conTredicesima().build());

        var tredicesima = voce(liquidazione(METALMECCANICO, null, cedolini), TipoVoceLiquidazione.RATEI_TREDICESIMA);

        assertThat(tredicesima.quantita()).as("aprile e maggio").isEqualByComparingTo("2");
    }

    @Test
    void ccnlA13MensilitaSenzaQuattordicesima() {
        var l = liquidazione(METALMECCANICO, null, inCorso(10, c -> { }));

        assertThat(l.voci()).extracting(VoceLiquidazione::tipo)
                .doesNotContain(TipoVoceLiquidazione.RATEI_QUATTORDICESIMA);
    }

    @Test
    void meseDellaQuattordicesimaIgnotoNonStimabile() {
        var senzaMese = new Ccnl("TELECOMUNICAZIONI", "Telecomunicazioni", 14, null, null, null, "Verificare");

        var v = voce(liquidazione(senzaMese, null, inCorso(10, c -> { })),
                TipoVoceLiquidazione.RATEI_QUATTORDICESIMA);

        assertThat(v.lordo()).isNull();
        assertThat(v.netto()).isNull();
        assertThat(v.nota()).contains("non è noto");
        assertThat(TELECOMUNICAZIONI.meseQuattordicesima()).as("il CCNL di prova ha il mese").isEqualTo(7);
    }

    @Test
    void cessazioneUsaLaDataDiCessazioneEIl15DelMese() {
        var cessazione = LocalDate.of(2025, 5, 10);
        var cedolini = inCorso(5, c -> c.date(ASSUNZIONE, cessazione));

        var l = liquidazione(METALMECCANICO, cessazione, cedolini);

        assertThat(l.dataRiferimento()).isEqualTo(cessazione);
        assertThat(voce(l, TipoVoceLiquidazione.RATEI_TREDICESIMA).quantita())
                .as("maggio lavorato per meno di 15 giorni").isEqualByComparingTo("4");
    }

    @Test
    void tfrLiquidatoNessunaStima() {
        var cessazione = LocalDate.of(2025, 5, 31);
        var cedolini = new ArrayList<>(inCorso(5, c -> c.date(ASSUNZIONE, cessazione)));
        cedolini.add(fineRapporto(1, 2025, 6).date(ASSUNZIONE, cessazione)
                .tfr(datiTfr(null, null, null, null, null, null, null, null, null, "10600.00", "2438.00")).build());

        assertThat(liquidazione(METALMECCANICO, cessazione, cedolini)).isEqualTo(Liquidazione.NESSUNA);
    }

    @Test
    void aliquotaContributiDaConfigurazioneSeNonStampata() {
        var cedolini = new ArrayList<CedolinoNormalizzato>();
        for (int mese = 1; mese <= 3; mese++) {
            cedolini.add(ordinario(2025, mese).date(ASSUNZIONE, null).base("2000.00").build());
        }

        var l = liquidazione(METALMECCANICO, null, cedolini);

        assertThat(l.aliquotaContributi()).isEqualByComparingTo("0.0919");
        assertThat(l.aliquotaContributiStampata()).isFalse();
        assertThat(l.nota()).contains("non stampata");
        var tfr = voce(l, TipoVoceLiquidazione.TFR);
        assertThat(tfr.lordo()).as("nessun dato TFR").isNull();
        assertThat(tfr.nota()).contains("non determinabile");
    }

    @Test
    void rivalutazioniNetteEsclusePerGliAnniConDati() {
        // assunzione a gennaio 2024: nessun fondo pregresso, rivalutazione 2024 di 100 con imposta 17
        var assunzione = LocalDate.of(2024, 1, 1);
        var cedolini = new ArrayList<CedolinoNormalizzato>();
        for (int mese = 1; mese <= 12; mese++) {
            cedolini.add(ordinario(2024, mese).date(assunzione, null).base("2000.00")
                    .tfr(datiTfr(null, mese == 12 ? "100.00" : null, mese == 12 ? "17.00" : null,
                            String.valueOf(150 * mese), "150.00", null, null, null, null, null, null))
                    .build());
        }
        cedolini.add(ordinario(2025, 1).date(assunzione, null).base("2000.00")
                .tfr(datiTfr("1883.00", null, null, "150.00", "150.00", null, null, null, null, null, null)).build());

        var l = calcolatore.calcola(contesto(assunzione, null, METALMECCANICO), cedolini, ORA).liquidazione();

        var tfr = voce(l, TipoVoceLiquidazione.TFR);
        // 2.033 in azienda, di cui 83 di rivalutazione netta gia' tassata: imponibile 1.950 al 23%
        assertThat(tfr.lordo()).isEqualByComparingTo("2033.00");
        assertThat(tfr.imposte()).isEqualByComparingTo("448.50");
        assertThat(tfr.nota()).contains("83,00 €").doesNotContain("anni senza dati");
    }

    @Test
    void saldoNegativoEUnDebito() {
        var l = liquidazione(METALMECCANICO, null,
                inCorso(3, c -> c.rateo(TipoRateo.ROL, UnitaMisura.ORE, "-4")));

        var rol = voce(l, TipoVoceLiquidazione.ROL);
        assertThat(rol.lordo().signum()).isNegative();
        assertThat(rol.netto().signum()).isNegative();
    }

    @Test
    void aliquotaMediaDelTfrConPiuScaglioni() {
        var scaglioni = PARAMETRI_LIQUIDAZIONE.scaglioni(2026);

        assertThat(scaglioni.imposta(new BigDecimal("60000"))).isEqualByComparingTo("18000");
        assertThat(scaglioni.aliquotaMarginale(new BigDecimal("28000"))).isEqualByComparingTo("0.23");
        assertThat(scaglioni.aliquotaMarginale(new BigDecimal("28000.01"))).isEqualByComparingTo("0.33");
        assertThat(PARAMETRI_LIQUIDAZIONE.scaglioni(2020).dalAnno()).as("prima del primo: il primo").isEqualTo(2024);
        assertThat(PARAMETRI_LIQUIDAZIONE.scaglioni(2025).dalAnno()).isEqualTo(2024);
    }

    @Test
    void aliquotaContributiStampataSommaLeVociInps() {
        var cedolino = ordinario(2025, 1).base("2000.00")
                .voce("Contributo IVS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, "9.19", "183.80")
                .voce("Contributo CIGS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, "0.30", "6.00")
                .voce("Fondo pensione", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, "0.55", "11.00")
                .build();

        assertThat(CalcoloLiquidazione.aliquotaContributi(cedolino)).hasValueSatisfying(
                a -> assertThat(a).isEqualByComparingTo("0.0949"));
    }
}
