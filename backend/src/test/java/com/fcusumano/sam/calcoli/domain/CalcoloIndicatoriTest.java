package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloIndicatoriTest {

    private static List<IndicatoriMensilita> indicatori(CedolinoDiProva... cedolini) {
        return CalcoloIndicatori.calcola(
                CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList()), PARAMETRI);
    }

    private static IndicatoriMensilita ultimo(CedolinoDiProva... cedolini) {
        return indicatori(cedolini).getLast();
    }

    @Test
    void primaMensilitaSenzaConfronto() {
        var i = indicatori(ordinario(2024, 1).scomposti("2000.00", "300.00", "25.00"));

        assertThat(i).singleElement().satisfies(x -> {
            assertThat(x.cedolinoId()).isEqualTo(new CedolinoId(20240100));
            assertThat(x.scattoInBusta()).isFalse();
            assertThat(x.aumentoFuoriScatti()).isFalse();
            assertThat(x.lordoContrattuale()).isEqualByComparingTo("2325.00");
        });
    }

    @Test
    void scattoDaImportoScattiNonEUnAumentoFuoriScatti() {
        var i = ultimo(ordinario(2023, 8).scomposti("2123.50", "176.50", "0"),
                ordinario(2023, 9).scomposti("2123.50", "176.50", "25.00"));

        assertThat(i.scattoInBusta()).isTrue();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void primoScattoConRigaScattiPrimaAssente() {
        var i = ultimo(ordinario(2023, 8).scomposti("2123.50", "176.50", null),
                ordinario(2023, 9).scomposti("2123.50", "176.50", "25.00"));

        assertThat(i.scattoInBusta()).isTrue();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void scattoDaNumeroScattiStampato() {
        var i = ultimo(ordinario(2023, 8).base("2300.00").scatti(0, null),
                ordinario(2023, 9).base("2300.00").scatti(1, null));

        assertThat(i.scattoInBusta()).isTrue();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void riassorbimentoDelSuperminimoAParitaDiTotaleNonEUnAumento() {
        var i = ultimo(ordinario(2023, 5).scomposti("2000.00", "300.00", null),
                ordinario(2023, 6).scomposti("2123.50", "176.50", null));

        assertThat(i.scattoInBusta()).isFalse();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void minimoCheCresceCompensatoSoloInParteEUnAumento() {
        var i = ultimo(ordinario(2023, 5).scomposti("2000.00", "300.00", null),
                ordinario(2023, 6).scomposti("2100.00", "240.00", null));

        assertThat(i.aumentoFuoriScatti()).isTrue();
        assertThat(i.lordoContrattuale()).isEqualByComparingTo("2340.00");
    }

    @Test
    void aumentoPerRinnovoConScattoNelloStessoMese() {
        var i = ultimo(ordinario(2023, 8).scomposti("2000.00", "300.00", "0"),
                ordinario(2023, 9).scomposti("2050.00", "300.00", "25.00"));

        assertThat(i.scattoInBusta()).isTrue();
        assertThat(i.aumentoFuoriScatti()).isTrue();
    }

    @Test
    void tolleranzaDiUnCentesimo() {
        assertThat(ultimo(ordinario(2024, 1).base("2000.00"), ordinario(2024, 2).base("2000.01"))
                .aumentoFuoriScatti()).isFalse();
        assertThat(ultimo(ordinario(2024, 1).base("2000.00"), ordinario(2024, 2).base("2000.02"))
                .aumentoFuoriScatti()).isTrue();
    }

    @Test
    void retribuzioneBaseNonScompostaChecresce() {
        var i = ultimo(ordinario(2024, 1).base("2500.00"), ordinario(2024, 2).base("2600.00"));

        assertThat(i.aumentoFuoriScatti()).isTrue();
        assertThat(i.scattoInBusta()).isFalse();
    }

    @Test
    void retribuzioneBaseConScattoNonSeparabileNessunAumento() {
        var i = ultimo(ordinario(2024, 1).base("2500.00").scatti(1, null),
                ordinario(2024, 2).base("2530.00").scatti(2, null));

        assertThat(i.scattoInBusta()).isTrue();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void importoScattiStampatoSuUnSoloCedolinoNonScompostoNonConfrontabile() {
        var conScatti = new ElementiRetributivi(null, null, null, null, euro("25.00"), null, euro("2525.00"),
                euro("2550.00"), null, null, null, null);
        var i = ultimo(ordinario(2024, 1).base("2500.00"), ordinario(2024, 2).elementi(conScatti));

        assertThat(i.scattoInBusta()).isFalse();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void calodelLordoNonEUnAumento() {
        var i = ultimo(ordinario(2024, 1).scomposti("2000.00", "300.00", "25.00"),
                ordinario(2024, 2).scomposti("2000.00", "200.00", "25.00"));

        assertThat(i.scattoInBusta()).isFalse();
        assertThat(i.aumentoFuoriScatti()).isFalse();
    }

    @Test
    void senzaLordoSuUnoDeiDueNessunIndicatoreDiAumento() {
        var i = indicatori(ordinario(2024, 1), ordinario(2024, 2).base("2600.00"), ordinario(2024, 3).base("2700.00"));

        assertThat(i.get(1).aumentoFuoriScatti()).isFalse();
        assertThat(i.get(0).lordoContrattuale()).isNull();
        assertThat(i.get(2).aumentoFuoriScatti()).isTrue();
    }

    @Test
    void cedoliniNonOrdinariHannoIndicatoriFalsiENonFannoDaConfronto() {
        var i = indicatori(
                ordinario(2024, 12).scomposti("2000.00", "300.00", "25.00"),
                tredicesima(2024).scomposti("2100.00", "300.00", "50.00"),
                ordinario(2025, 1).scomposti("2000.00", "300.00", "25.00"),
                fineRapporto(1, 2025, 2).scomposti("2500.00", "300.00", "25.00"));

        assertThat(i).extracting(IndicatoriMensilita::cedolinoId).containsExactly(
                new CedolinoId(20241200), new CedolinoId(20241250), new CedolinoId(20250100), new CedolinoId(20250251));
        assertThat(i).allSatisfy(x -> {
            assertThat(x.scattoInBusta()).isFalse();
            assertThat(x.aumentoFuoriScatti()).isFalse();
        });
        assertThat(i.get(1).lordoContrattuale()).isEqualByComparingTo("2450.00");
    }

    @Test
    void confrontoConLaMensilitaOrdinariaPrecedenteAncheSeNonContigua() {
        var i = ultimo(ordinario(2023, 4).scomposti("2000.00", "300.00", null),
                ordinario(2023, 6).scomposti("2000.00", "350.00", null));

        assertThat(i.aumentoFuoriScatti()).isTrue();
    }
}
