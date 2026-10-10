package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.COMMERCIO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.METALMECCANICO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.TELECOMUNICAZIONI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Scatti;
import com.fcusumano.sam.ccnl.Ccnl;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloScattiTest {

    private static Scatti scatti(ContestoRapporto contesto, CedolinoDiProva... cedolini) {
        var ordinati = CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList());
        return CalcoloScatti.calcola(contesto, ordinati, CalcoloAnzianita.calcola(contesto, ordinati));
    }

    @Test
    void numeroEDataStampatiPrevalgono() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO),
                ordinario(2024, 1).scatti(1, LocalDate.of(2025, 9, 1)));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(s.attualiStimati()).isFalse();
        assertThat(s.massimo()).isEqualTo(5);
        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CEDOLINI);
    }

    @Test
    void dataStampataPrevaleAncheSuDiversaDaQuellaCalcolata() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO),
                ordinario(2024, 1).scatti(null, LocalDate.of(2025, 12, 1)));

        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CEDOLINI);
        // il numero non e' stampato: stimato dal CCNL
        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.attualiStimati()).isTrue();
    }

    @Test
    void numeroDallUltimoCedolinoCheLoRiportaSenzaRegoleEUnaStimaSeNonERecente() {
        var s = scatti(contesto(null, null, null),
                ordinario(2023, 8).scatti(0, null), ordinario(2023, 9).scatti(1, null), ordinario(2023, 10));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.massimo()).isNull();
        assertThat(s.prossimoScatto()).isNull();
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void stimaDalCcnlConPeriodicitaEMassimoNoti() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO), ordinario(2024, 6));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CCNL);
        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CCNL);
    }

    @Test
    void loScattoDecorreDalMeseSuccessivoAlCompimentoDelPeriodo() {
        assertThat(CalcoloScatti.decorrenza(METALMECCANICO, LocalDate.of(2021, 9, 1), 1))
                .isEqualTo(LocalDate.of(2023, 9, 1));
        assertThat(CalcoloScatti.decorrenza(METALMECCANICO, LocalDate.of(2021, 9, 16), 1))
                .isEqualTo(LocalDate.of(2023, 10, 1));
        assertThat(CalcoloScatti.decorrenza(COMMERCIO, LocalDate.of(2024, 6, 16), 2))
                .isEqualTo(LocalDate.of(2030, 7, 1));
        // Agosto 2023 non ha ancora lo scatto, settembre si'
        assertThat(CalcoloScatti.scattiMaturati(METALMECCANICO, LocalDate.of(2021, 9, 1), YearMonth.of(2023, 8)))
                .isZero();
        assertThat(CalcoloScatti.scattiMaturati(METALMECCANICO, LocalDate.of(2021, 9, 1), YearMonth.of(2023, 9)))
                .isEqualTo(1);
    }

    @Test
    void stimaNonSuperaIlMassimoENonPrevedeAltriScatti() {
        var s = scatti(contesto(LocalDate.of(2005, 1, 1), null, METALMECCANICO), ordinario(2024, 6));

        assertThat(s.attuali()).isEqualTo(5);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.prossimoScatto()).isNull();
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void numeroStampatoAlMassimoNessunProssimoScatto() {
        var s = scatti(contesto(LocalDate.of(2005, 1, 1), null, METALMECCANICO), ordinario(2024, 6).scatti(5, null));

        assertThat(s.attuali()).isEqualTo(5);
        assertThat(s.prossimoScatto()).isNull();
    }

    @Test
    void numeroStampatoEProssimoCalcolatoDalCcnl() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO), ordinario(2024, 1).scatti(1, null));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.attualiStimati()).isFalse();
        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CCNL);
    }

    @Test
    void numeroStampatoIncoerenteConLeRegoleNessunaPrevisione() {
        // Dal calendario del CCNL il secondo scatto sarebbe gia' decorso (settembre 2025) ma la busta ne riporta uno
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO), ordinario(2026, 1).scatti(1, null));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.prossimoScatto()).isNull();
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void dataStampataGiaTrascorsaIgnorataEProssimoDalCcnl() {
        // vecchio layout: numero e data stampati solo a marzo 2019, i cedolini del 2024 non li riportano
        var s = scatti(contesto(LocalDate.of(2017, 1, 1), null, METALMECCANICO),
                ordinario(2019, 3).scatti(1, LocalDate.of(2021, 1, 1)), ordinario(2024, 5), ordinario(2024, 6));

        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CCNL);
    }

    @Test
    void dataStampataFuturaSuUnCedolinoPrecedenteResta() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), null, METALMECCANICO),
                ordinario(2024, 1).scatti(1, LocalDate.of(2025, 9, 1)), ordinario(2024, 2));

        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.CEDOLINI);
    }

    @Test
    void numeroStampatoSoloSuUnVecchioCedolinoAggiornatoConLeRegoleDelCcnl() {
        var s = scatti(contesto(LocalDate.of(2017, 1, 1), null, METALMECCANICO),
                ordinario(2019, 3).scatti(1, null), ordinario(2024, 6));

        // 1 a marzo 2019 + scatti di gennaio 2021 e gennaio 2023
        assertThat(s.attuali()).isEqualTo(3);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CCNL);
    }

    @Test
    void numeroStampatoSoloSuUnVecchioCedolinoSenzaRegoleEUnaStima() {
        var s = scatti(contesto(LocalDate.of(2017, 1, 1), null, null),
                ordinario(2019, 3).scatti(1, null), ordinario(2024, 6));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CEDOLINI);
    }

    @Test
    void numeroStampatoSullUltimaOrdinariaNonEStimato() {
        var s = scatti(contesto(LocalDate.of(2017, 1, 1), null, METALMECCANICO),
                ordinario(2024, 5).scatti(3, null), ordinario(2024, 6).scatti(3, null));

        assertThat(s.attuali()).isEqualTo(3);
        assertThat(s.attualiStimati()).isFalse();
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.CEDOLINI);
    }

    @Test
    void numeroStampatoSuperioreAiMaturabiliNessunaPrevisione() {
        // anzianita' riconosciuta da un precedente datore: 3 scatti dopo due anni e mezzo di rapporto
        var s = scatti(contesto(LocalDate.of(2022, 1, 1), null, COMMERCIO), ordinario(2024, 6).scatti(3, null));

        assertThat(s.attuali()).isEqualTo(3);
        assertThat(s.prossimoScatto()).isNull();
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void ccnlSenzaRegoleScattiNessunaStima() {
        var s = scatti(contesto(LocalDate.of(2015, 1, 1), null, TELECOMUNICAZIONI), ordinario(2024, 6));

        assertThat(s.attuali()).isNull();
        assertThat(s.fonteAttuali()).isEqualTo(FonteDato.NON_DISPONIBILE);
        assertThat(s.attualiStimati()).isFalse();
        assertThat(s.massimo()).isNull();
        assertThat(s.prossimoScatto()).isNull();
    }

    @Test
    void ccnlNonIndicatoNessunaStima() {
        var s = scatti(contesto(LocalDate.of(2015, 1, 1), null, null), ordinario(2024, 6));

        assertThat(s.attuali()).isNull();
        assertThat(s.massimo()).isNull();
        assertThat(s.prossimoScatto()).isNull();
    }

    @Test
    void senzaAssunzioneLaStimaParteDalPrimoCedolinoOrdinario() {
        var s = scatti(contesto(null, null, COMMERCIO), ordinario(2020, 3), ordinario(2024, 6));

        // inizio 1/3/2020, periodo triennale: primo scatto da marzo 2023
        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.attualiStimati()).isTrue();
        assertThat(s.prossimoScatto()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void rapportoCessatoNonHaUnProssimoScatto() {
        var s = scatti(contesto(LocalDate.of(2021, 9, 1), LocalDate.of(2024, 6, 15), METALMECCANICO),
                ordinario(2024, 6).scatti(1, LocalDate.of(2025, 9, 1)));

        assertThat(s.attuali()).isEqualTo(1);
        assertThat(s.prossimoScatto()).isNull();
        assertThat(s.fonteProssimo()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void cambioCcnlCambiaLaStima() {
        var metalmeccanico = scatti(contesto(LocalDate.of(2018, 1, 1), null, METALMECCANICO), ordinario(2024, 6));
        var commercio = scatti(contesto(LocalDate.of(2018, 1, 1), null, COMMERCIO), ordinario(2024, 6));
        var senzaRegole = scatti(contesto(LocalDate.of(2018, 1, 1), null,
                new Ccnl("ALTRO_13", "Altro", 13, null, null, null, null)), ordinario(2024, 6));

        assertThat(metalmeccanico.attuali()).isEqualTo(3);
        assertThat(commercio.attuali()).isEqualTo(2);
        assertThat(commercio.massimo()).isEqualTo(10);
        assertThat(senzaRegole.attuali()).isNull();
    }
}
