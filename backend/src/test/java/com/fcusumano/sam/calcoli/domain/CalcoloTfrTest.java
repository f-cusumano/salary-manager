package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.datiTfr;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloTfrTest {

    private static final LocalDate ASSUNZIONE = LocalDate.of(2021, 9, 1);

    private static Tfr tfr(ContestoRapporto contesto, CedolinoDiProva... cedolini) {
        var ordinati = CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList());
        return CalcoloTfr.calcola(contesto, ordinati, CalcoloAnzianita.calcola(contesto, ordinati), PARAMETRI);
    }

    private static Tfr tfr(CedolinoDiProva... cedolini) {
        return tfr(contesto(ASSUNZIONE, null, null), cedolini);
    }

    // ------------------------------------------------------------------------------------------------ formula

    @Test
    void accumulatoInAziendaDaFondoRivalutazioneQuoteEAnticipi() {
        var t = tfr(ordinario(2024, 6).tfr(
                datiTfr("4526.32", "31.20", "5.30", "883.30", "80.30", null, null, "3000.00", "1162.50", null, null)));

        // 4526,32 + 31,20 - 5,30 + 883,30 - 3000,00
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("2435.52");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("2435.52");
        assertThat(t.quotaMensile()).isEqualByComparingTo("80.30");
        assertThat(t.quotaMensileStimata()).isFalse();
        assertThat(t.anticipi()).isEqualByComparingTo("3000.00");
        assertThat(t.quotaFondi()).isEqualByComparingTo("0");
        assertThat(t.quotaBusta()).isEqualByComparingTo("0");
        // stima netto: (2435,52 - 25,90) x (1 - 0,23) + 25,90 (rivalutazione netta gia' tassata)
        assertThat(t.stimaNetto()).isEqualByComparingTo("1881.31");
        assertThat(t.aliquotaStimata()).isEqualByComparingTo("0.23");
        assertThat(t.stimato()).isFalse();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_NETTO_RIVALUTAZIONE);
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(t.liquidato()).isFalse();
        assertThat(t.importoLiquidato()).isNull();
    }

    @Test
    void dallUltimoCedolinoConDatiTfr() {
        var t = tfr(
                ordinario(2024, 1).tfr(datiTfr("4526.32", null, null, "160.60", "160.60", null, null, null, null, null, null)),
                ordinario(2024, 2).tfr(datiTfr("4526.32", null, null, "321.20", "160.60", null, null, null, null, null, null)),
                ordinario(2024, 3));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("4847.52");
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 2));
    }

    @Test
    void anticipiProgressiviNonVengonoSommatiDueVolte() {
        var t = tfr(
                ordinario(2024, 2).tfr(datiTfr("4526.32", null, null, "321.20", "160.60", null, null, "3000.00", null, null, null)),
                ordinario(2024, 3).tfr(datiTfr("4526.32", null, null, "481.80", "160.60", null, null, "3000.00", null, null, null)));

        assertThat(t.anticipi()).isEqualByComparingTo("3000.00");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("2008.12");
        assertThat(t.stimato()).isFalse();
    }

    @Test
    void anticipoStampatoSoloNelMeseDiErogazioneVieneComunqueDedotto() {
        var t = tfr(
                ordinario(2024, 2).tfr(datiTfr("4526.32", null, null, "321.20", "160.60", null, null, "3000.00", null, null, null)),
                ordinario(2024, 3).tfr(datiTfr("4526.32", null, null, "481.80", "160.60", null, null, null, null, null, null)));

        assertThat(t.anticipi()).isEqualByComparingTo("3000.00");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("2008.12");
    }

    @Test
    void anticipiMensiliDiversiVengonoSommatiComeStima() {
        var t = tfr(
                ordinario(2024, 2).tfr(datiTfr("4526.32", null, null, "321.20", "160.60", null, null, "1000.00", null, null, null)),
                ordinario(2024, 5).tfr(datiTfr("4526.32", null, null, "803.00", "160.60", null, null, "500.00", null, null, null)));

        assertThat(t.anticipi()).isEqualByComparingTo("1500.00");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("3829.32");
        assertThat(t.stimato()).isTrue();
        assertThat(t.nota()).contains(CalcoloTfr.NOTA_IMPORTI_MENSILI);
    }

    @Test
    void quoteAiFondiEInBustaSottratteDallAziendaEFondiNelTotale() {
        var t = tfr(contesto(LocalDate.of(2024, 1, 10), null, null),
                ordinario(2024, 3).tfr(datiTfr("0", null, null, "480.00", "160.00", "300.00", "60.00", null, null, null, null)));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("120.00");
        assertThat(t.quotaFondi()).isEqualByComparingTo("300.00");
        assertThat(t.quotaBusta()).isEqualByComparingTo("60.00");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("420.00");
        // la stima del netto riguarda solo la quota in azienda
        assertThat(t.stimaNetto()).isEqualByComparingTo("92.40");
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_FONDI);
        assertThat(t.stimato()).isFalse();
    }

    @Test
    void quotaAiFondiStampataComeImportoDelMeseVieneSommata() {
        // il valore coincide con la quota del mese mentre la quota dell'anno e' maggiore: importo del mese
        var t = tfr(contesto(LocalDate.of(2024, 1, 10), null, null),
                ordinario(2024, 1).tfr(datiTfr(null, null, null, "160.00", "160.00", "160.00", null, null, null, null, null)),
                ordinario(2024, 2).tfr(datiTfr(null, null, null, "320.00", "160.00", "160.00", null, null, null, null, null)),
                ordinario(2024, 3).tfr(datiTfr(null, null, null, "480.00", "160.00", "160.00", null, null, null, null, null)));

        assertThat(t.quotaFondi()).isEqualByComparingTo("480.00");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("0");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("480.00");
        assertThat(t.stimato()).isTrue();
    }

    @Test
    void quoteAiFondiDegliAnniPrecedentiNelTotaleConAnniMancanti() {
        var t = tfr(contesto(LocalDate.of(2020, 3, 1), null, null),
                ordinario(2022, 12).tfr(datiTfr("0", null, null, "1900.00", "160.00", "1900.00", null, null, null, null, null)),
                ordinario(2024, 2).tfr(datiTfr("0", null, null, "320.00", "160.00", "320.00", null, null, null, null, null)));

        assertThat(t.quotaFondi()).isEqualByComparingTo("2220.00");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("0");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("2220.00");
        assertThat(t.stimato()).isTrue();
        assertThat(t.nota()).contains("Mancano i dati TFR di: 2020-2021, 2023;");
    }

    // ------------------------------------------------------------------------------------------------ dati mancanti

    @Test
    void fondoAssenteConRapportoIniziatoNellAnnoValeZero() {
        var t = tfr(contesto(LocalDate.of(2024, 6, 16), null, null),
                ordinario(2024, 8).tfr(datiTfr(null, null, null, "445.00", "192.59", null, null, null, "2600.00", null, null)));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("445.00");
        assertThat(t.stimato()).isFalse();
    }

    @Test
    void fondoAssenteConRapportoPrecedenteNonDeterminabile() {
        var t = tfr(ordinario(2024, 8).tfr(datiTfr(null, null, null, "1280.00", "160.00", null, null, null, null, null, null)));

        assertThat(t.totaleAccumulato()).isNull();
        assertThat(t.quotaInAzienda()).isNull();
        assertThat(t.stimaNetto()).isNull();
        assertThat(t.quotaMensile()).isEqualByComparingTo("160.00");
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_FONDO_ASSENTE);
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 8));
    }

    @Test
    void fondoAssenteSenzaDataDiAssunzioneNonDeterminabileAncheSeIlPrimoCedolinoENellAnno() {
        var t = tfr(contesto(null, null, null),
                ordinario(2024, 8).tfr(datiTfr(null, null, null, "1280.00", "160.00", null, null, null, null, null, null)));

        assertThat(t.totaleAccumulato()).isNull();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_FONDO_ASSENTE);
    }

    @Test
    void quotaAnnoAssenteRicostruitaDalleQuoteMensili() {
        var t = tfr(
                ordinario(2024, 1).tfr(datiTfr("1000.00", null, null, null, "160.00", null, null, null, null, null, null)),
                ordinario(2024, 2).tfr(datiTfr("1000.00", null, null, null, "165.00", null, null, null, null, null, null)));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("1325.00");
        assertThat(t.stimato()).isTrue();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_QUOTA_ANNO_SOMMATA);
    }

    @Test
    void quotaAnnoEQuoteMensiliAssentiNonIncluse() {
        var t = tfr(ordinario(2024, 1).tfr(datiTfr("1000.00", null, null, null, null, null, null, null, null, null, null)));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("1000.00");
        assertThat(t.stimato()).isTrue();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_QUOTA_ANNO_ASSENTE);
        assertThat(t.quotaMensile()).isNull();
    }

    @Test
    void impostaSostitutivaAssenteStimataDallaRivalutazione() {
        var t = tfr(ordinario(2024, 12).tfr(
                datiTfr("2410.35", "50.62", null, "1913.36", "160.60", null, null, null, null, null, null)));

        // 2410,35 + 50,62 - 50,62 x 17% + 1913,36
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("4365.7246");
        assertThat(t.stimato()).isTrue();
        assertThat(t.nota()).startsWith(
                "Imposta sostitutiva sulla rivalutazione non stampata: stimata al 17% della rivalutazione.");
    }

    @Test
    void quotaMensileNonStampataStimataDallaRetribuzioneUtile() {
        var t = tfr(ordinario(2024, 1).tfr(
                datiTfr("1000.00", null, null, "172.22", null, null, null, null, "2325.00", null, null)));

        // 2325 / 13,5
        assertThat(t.quotaMensile()).isEqualByComparingTo("172.22222");
        assertThat(t.quotaMensileStimata()).isTrue();
        assertThat(t.stimato()).isFalse();
    }

    @Test
    void quotaMensileDallUltimaOrdinariaNonDallaTredicesima() {
        var t = tfr(
                ordinario(2024, 12).tfr(datiTfr("1000.00", null, null, "1800.00", "150.00", null, null, null, null, null, null)),
                tredicesima(2024).tfr(datiTfr("1000.00", null, null, "1950.00", "150.00", null, null, null, null, null, null)));

        assertThat(t.quotaInAzienda()).isEqualByComparingTo("2950.00");
        assertThat(t.quotaMensile()).isEqualByComparingTo("150.00");
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 12));
    }

    // ------------------------------------------------------------------------------------------------ dati dell'anno

    @Test
    void tredicesimaSeparataSenzaRivalutazioneUsaLaRivalutazioneDiDicembre() {
        var t = tfr(
                ordinario(2023, 12).tfr(datiTfr("2410.35", "50.62", "8.61", "1913.36", "160.60", null, null, null, null, null, null)),
                tredicesima(2023).tfr(datiTfr("2410.35", null, null, "2073.96", null, null, null, null, null, null, null)));

        // 2410,35 + 50,62 - 8,61 + 2073,96 (quota dell'anno incrementata dalla tredicesima)
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("4526.32");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("4526.32");
        assertThat(t.stimato()).isFalse();
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2023, 12));
    }

    @Test
    void tredicesimaConLaSolaQuotaDelMeseUsaIDatiDiDicembre() {
        var t = tfr(
                ordinario(2023, 12).tfr(datiTfr("2410.35", "50.62", "8.61", "1913.36", "160.60", null, null, null, null, null, null)),
                tredicesima(2023).tfr(datiTfr(null, null, null, null, "160.60", null, null, null, null, null, null)));

        // 2410,35 + 50,62 - 8,61 + 1913,36
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("4365.72");
        assertThat(t.stimato()).isFalse();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_NETTO_RIVALUTAZIONE);
    }

    @Test
    void fineRapportoSenzaLiquidazioneNeFondoUsaIlFondoDelleOrdinarieDellAnno() {
        var t = tfr(contesto(ASSUNZIONE, LocalDate.of(2024, 5, 31), null),
                ordinario(2024, 4).tfr(datiTfr("3000.00", null, null, "640.00", "160.00", null, null, null, null, null, null)),
                ordinario(2024, 5).tfr(datiTfr("3000.00", null, null, "800.00", "160.00", null, null, null, null, null, null)),
                fineRapporto(1, 2024, 6).tfr(datiTfr(null, null, null, "960.00", "160.00", null, null, null, null, null, null)));

        assertThat(t.liquidato()).isFalse();
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("3960.00");
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("3960.00");
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 6));
    }

    @Test
    void laStimaDelNettoNonTassaDiNuovoLaRivalutazioneNettaDellAnno() {
        var t = tfr(
                ordinario(2023, 12).tfr(datiTfr("2410.35", "50.62", "8.61", "1913.36", "160.60", null, null, null, null, null, null)),
                tredicesima(2023).tfr(datiTfr("2410.35", null, null, "2073.96", null, null, null, null, null, null, null)));

        // (4526,32 - 42,01) x 0,77 + 42,01
        assertThat(t.stimaNetto()).isEqualByComparingTo("3494.93");
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_NETTO_RIVALUTAZIONE);
    }

    @Test
    void accumulatoNegativoEIncoerente() {
        var t = tfr(ordinario(2024, 3).tfr(
                datiTfr("100.00", null, null, "480.00", "160.00", null, null, "5000.00", null, null, null)));

        assertThat(t.totaleAccumulato()).isNull();
        assertThat(t.quotaInAzienda()).isNull();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_INCOERENTE);
    }

    @Test
    void nessunDatoTfr() {
        var t = tfr(ordinario(2024, 3), ordinario(2024, 4).tfr(
                datiTfr(null, null, null, null, null, null, null, null, null, null, null)));

        assertThat(t.totaleAccumulato()).isNull();
        assertThat(t.quotaMensile()).isNull();
        assertThat(t.stimaNetto()).isNull();
        assertThat(t.riferimento()).isNull();
        assertThat(t.liquidato()).isFalse();
        assertThat(t.nota()).isEqualTo(CalcoloTfr.NOTA_NESSUN_DATO);
    }

    // ------------------------------------------------------------------------------------------------ liquidazione

    @Test
    void tfrLiquidatoNelCedolinoDiFineRapporto() {
        var t = tfr(contesto(ASSUNZIONE, LocalDate.of(2024, 6, 15), null),
                ordinario(2024, 6).tfr(datiTfr("4526.32", null, null, "883.30", "80.30", null, null, "3000.00", "1162.50", null, null)),
                fineRapporto(1, 2024, 7).tfr(
                        datiTfr("4526.32", "31.20", "5.30", "883.30", null, null, null, "3000.00", null, "2435.52", "497.89")));

        assertThat(t.liquidato()).isTrue();
        assertThat(t.totaleAccumulato()).isEqualByComparingTo("0");
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("0");
        assertThat(t.importoLiquidato()).isEqualByComparingTo("2435.52");
        assertThat(t.nettoLiquidato()).isEqualByComparingTo("1937.63");
        assertThat(t.stimaNetto()).isNull();
        assertThat(t.aliquotaStimata()).isNull();
        assertThat(t.quotaMensile()).isNull();
        assertThat(t.stimato()).isFalse();
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 7));
        assertThat(t.nota()).isEqualTo(
                "TFR liquidato nel cedolino di fine rapporto di Luglio 2024: nessun TFR residuo in azienda.");
        assertThat(t.anticipi()).isEqualByComparingTo("3000.00");
    }

    @Test
    void liquidazioneInPiuCedoliniSenzaRitenutaNettoNonDisponibile() {
        var t = tfr(contesto(ASSUNZIONE, LocalDate.of(2024, 6, 15), null),
                fineRapporto(1, 2024, 7).tfr(datiTfr(null, null, null, null, null, null, null, null, null, "2000.00", "400.00")),
                fineRapporto(2, 2024, 9).tfr(datiTfr(null, null, null, null, null, null, null, null, null, "435.52", null)));

        assertThat(t.liquidato()).isTrue();
        assertThat(t.importoLiquidato()).isEqualByComparingTo("2435.52");
        assertThat(t.nettoLiquidato()).isNull();
        assertThat(t.riferimento()).isEqualTo(YearMonth.of(2024, 9));
    }

    @Test
    void liquidazioneNelCedolinoOrdinarioDelMeseDiCessazione() {
        var t = tfr(contesto(ASSUNZIONE, LocalDate.of(2024, 6, 15), null),
                ordinario(2024, 6).tfr(datiTfr("4526.32", null, null, "883.30", "80.30", null, null, null, null, "5409.62", "1100.00")));

        assertThat(t.liquidato()).isTrue();
        assertThat(t.nettoLiquidato()).isEqualByComparingTo("4309.62");
    }

    @Test
    void importoLiquidatoSuCedolinoOrdinarioSenzaCessazioneNonELiquidazione() {
        var t = tfr(ordinario(2024, 3).tfr(
                datiTfr("4526.32", null, null, "481.80", "160.60", null, null, null, null, "1000.00", null)));

        assertThat(t.liquidato()).isFalse();
        assertThat(t.quotaInAzienda()).isEqualByComparingTo("5008.12");
    }
}
