package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.DiFineRapporto;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.Mensile;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DeterminazioneCompetenzaTest {

    private static final YearMonth DICEMBRE_2023 = YearMonth.of(2023, 12);
    private static final LocalDate CESSAZIONE = LocalDate.of(2024, 6, 15);

    @Test
    void ordinariaSenzaCessazioneEIlMeseDelPeriodo() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.ORDINARIA, DICEMBRE_2023, null, null))
                .isEqualTo(new Mensile(new Competenza.Mensilita(2023, 12)));
    }

    @Test
    void tredicesimaEQuattordicesimaSonoMese13E14DellAnnoDelPeriodo() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.TREDICESIMA, DICEMBRE_2023, null, null))
                .isEqualTo(new Mensile(Competenza.Mensilita.tredicesima(2023)));
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.QUATTORDICESIMA, YearMonth.of(2024, 7), null, null))
                .isEqualTo(new Mensile(Competenza.Mensilita.quattordicesima(2024)));
    }

    @Test
    void fineRapportoDichiaratoRestaFineRapportoSulPeriodoStampato() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 7), null, null))
                .isEqualTo(new DiFineRapporto(YearMonth.of(2024, 7)));
    }

    @Test
    void ordinariaSuccessivaAlMeseDiCessazioneEFineRapporto() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), null, CESSAZIONE))
                .isEqualTo(new DiFineRapporto(YearMonth.of(2024, 7)));
    }

    @Test
    void ilMeseDiCessazioneRestaOrdinario() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.ORDINARIA, YearMonth.of(2024, 6), null, CESSAZIONE))
                .isEqualTo(new Mensile(new Competenza.Mensilita(2024, 6)));
    }

    @Test
    void unaRiassunzioneSuccessivaAllaCessazioneNonEFineRapporto() {
        var riassunzione = LocalDate.of(2024, 9, 1);
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.ORDINARIA, YearMonth.of(2024, 10), riassunzione,
                CESSAZIONE)).isEqualTo(new Mensile(new Competenza.Mensilita(2024, 10)));
    }

    @Test
    void tredicesimaSuccessivaAlMeseDiCessazioneEFineRapportoSulPeriodoStampato() {
        var cessazione = LocalDate.of(2024, 9, 30);
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), null,
                cessazione)).isEqualTo(new DiFineRapporto(YearMonth.of(2024, 12)));
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.QUATTORDICESIMA, YearMonth.of(2024, 10), null,
                cessazione)).isEqualTo(new DiFineRapporto(YearMonth.of(2024, 10)));
    }

    @Test
    void tredicesimaNelMeseDiCessazioneOSenzaCessazioneResta13() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), null,
                LocalDate.of(2024, 12, 15))).isEqualTo(new Mensile(Competenza.Mensilita.tredicesima(2024)));
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), null, null))
                .isEqualTo(new Mensile(Competenza.Mensilita.tredicesima(2024)));
    }

    @Test
    void tredicesimaDopoUnaRiassunzioneResta13() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12),
                LocalDate.of(2024, 9, 1), CESSAZIONE)).isEqualTo(new Mensile(Competenza.Mensilita.tredicesima(2024)));
    }

    @Test
    void aggiuntivaRestituisceLaMensilitaConCuiConfligge() {
        assertThat(DeterminazioneCompetenza.determina(TipoDichiarato.AGGIUNTIVA, YearMonth.of(2024, 3), null, null))
                .isEqualTo(new Mensile(new Competenza.Mensilita(2024, 3)));
    }
}
