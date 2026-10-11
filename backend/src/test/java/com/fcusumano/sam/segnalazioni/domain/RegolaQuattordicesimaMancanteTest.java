package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinari;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.quattordicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.COMMERCIO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.METALMECCANICO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.TELECOMUNICAZIONI;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegolaQuattordicesimaMancanteTest {

    private final RegolaQuattordicesimaMancante regola = new RegolaQuattordicesimaMancante();

    private static ContestoDiProva commercioDa2024(YearMonth fino) {
        return rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), fino));
    }

    @Test
    void segnalaLaQuattordicesimaMancanteDopoIlMeseDiPagamento() {
        var segnalazioni = regola.valuta(commercioDa2024(YearMonth.of(2024, 8)).build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.QUATTORDICESIMA_MANCANTE);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.quattordicesima(2024));
            assertThat(s.titolo()).isEqualTo("Quattordicesima 2024 mancante");
            assertThat(s.descrizione()).contains("a Luglio", "per il 2024", "successivi a Luglio 2024");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione).contains(
                    "Cedolino del mese di pagamento: Luglio 2024", "Primo cedolino successivo: Agosto 2024");
        });
    }

    @Test
    void ccnlNonNotoNessunaSegnalazione() {
        var contesto = rapporto().assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 12))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void ccnlConTrediciMensilitaNessunaSegnalazione() {
        var contesto = rapporto().ccnl(METALMECCANICO).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 12))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void meseDiPagamentoNonAncoraSuperatoNessunaSegnalazione() {
        assertThat(regola.valuta(commercioDa2024(YearMonth.of(2024, 7)).build())).isEmpty();
    }

    @Test
    void cedolinoDelMeseDiPagamentoNonCaricatoNessunaSegnalazione() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 6)))
                .con(ordinario(2024, 8), ordinario(2024, 9)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void quattordicesimaPagataNelCedolinoDiLuglio() {
        var contesto = commercioDa2024(YearMonth.of(2024, 8)).sostituisci(ordinario(2024, 7).conQuattordicesima())
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cedolinoDiQuattordicesimaPresente() {
        var contesto = commercioDa2024(YearMonth.of(2024, 8)).con(quattordicesima(2024, 7)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void assuntoTroppoTardiNelPeriodoDiMaturazioneNonMaturaRatei() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2024, 6, 20))
                .con(ordinari(YearMonth.of(2024, 6), YearMonth.of(2024, 9))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void assuntoConQuindiciGiorniNelPeriodoDiMaturazioneSegnala() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2024, 6, 16))
                .con(ordinari(YearMonth.of(2024, 6), YearMonth.of(2024, 9))).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(Competenza.Mensilita.quattordicesima(2024));
    }

    @Test
    void assunzioneNonNotaConPrimoCedolinoNellUltimoMeseDiMaturazioneNonSegnala() {
        var contesto = rapporto().ccnl(COMMERCIO).con(ordinari(YearMonth.of(2024, 6), YearMonth.of(2024, 9))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void assunzioneNonNotaConCedoliniPrecedentiSegnala() {
        var contesto = rapporto().ccnl(COMMERCIO).con(ordinari(YearMonth.of(2024, 5), YearMonth.of(2024, 9))).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void cessatoPrimaDelMeseDiPagamentoNessunaSegnalazione() {
        var contesto = commercioDa2024(YearMonth.of(2024, 6)).cessazione(LocalDate.of(2024, 6, 30))
                .con(CedolinoDiProva.fineRapporto(1, 2024, 8)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void meseDiPagamentoDaVerificareRichiedeLAnnoConcluso() {
        var soloAgosto = rapporto().ccnl(TELECOMUNICAZIONI).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 8))).build();
        var annoConcluso = rapporto().ccnl(TELECOMUNICAZIONI).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2025, 1))).build();

        assertThat(regola.valuta(soloAgosto)).isEmpty();
        assertThat(regola.valuta(annoConcluso)).singleElement().satisfies(s -> {
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.quattordicesima(2024));
            assertThat(s.descrizione()).doesNotContain("a Luglio").contains("successivi a Dicembre 2024");
        });
    }

    @Test
    void valutaOgniAnnoSeparatamente() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2020, 3, 1))
                .con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2024, 8)))
                .sostituisci(ordinario(2023, 7).conQuattordicesima())
                .build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(Competenza.Mensilita.quattordicesima(2024));
    }

    /** Mensilita' ordinarie dell'anno con il testo del CCNL stampato. */
    private static CedolinoDiProva[] anno(int anno, String ccnlStampato) {
        return IntStream.rangeClosed(1, 12)
                .mapToObj(mese -> ordinario(anno, mese).ccnl(ccnlStampato))
                .toArray(CedolinoDiProva[]::new);
    }

    @Test
    void cambioDiCcnlStampatoNessunaSegnalazionePerGliAnniDelContrattoPrecedente() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2022, 1, 1))
                .con(anno(2022, "CCNL Metalmeccanico industria"))
                .con(anno(2023, "CCNL Commercio terziario"))
                .con(quattordicesima(2023, 7).ccnl("CCNL Commercio terziario"))
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void stessoCcnlStampatoInTuttiGliAnniSegnalaComePrima() {
        var contesto = rapporto().ccnl(COMMERCIO).assunzione(LocalDate.of(2022, 1, 1))
                .con(anno(2022, "CCNL Commercio terziario"))
                .con(anno(2023, "CCNL Commercio terziario"))
                .con(quattordicesima(2023, 7).ccnl("CCNL Commercio terziario"))
                .build();

        assertThat(regola.valuta(contesto)).singleElement()
                .satisfies(s -> assertThat(s.titolo()).isEqualTo("Quattordicesima 2022 mancante"));
    }

    @Test
    void quattordicesimaConUnFileInErroreAssociatoNonEMancante() {
        var contesto = commercioDa2024(YearMonth.of(2024, 8))
                .inErrore(Competenza.Mensilita.quattordicesima(2024), null).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }
}
