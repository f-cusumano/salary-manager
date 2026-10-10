package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinari;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.tredicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RegolaMensilitaMancanteTest {

    private final RegolaMensilitaMancante regola = new RegolaMensilitaMancante();

    @Test
    void segnalaIlMeseMancanteTraPrimoEUltimoCedolinoOrdinario() {
        var contesto = rapporto().con(ordinario(2024, 1), ordinario(2024, 2), ordinario(2024, 4)).build();

        var segnalazioni = regola.valuta(contesto);

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.MENSILITA_MANCANTE);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.codiceRegola()).isEqualTo("MENSILITA_MANCANTE");
            assertThat(s.competenza()).isEqualTo(new Competenza.Mensilita(2024, 3));
            assertThat(s.cedolinoId()).isNull();
            assertThat(s.titolo()).isEqualTo("Mensilità mancante: Marzo 2024");
            assertThat(s.descrizione()).contains("Marzo 2024", "da Gennaio 2024 a Aprile 2024");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione)
                    .containsExactly("Cedolino precedente: Febbraio 2024", "Cedolino successivo: Aprile 2024");
        });
    }

    @Test
    void unaSegnalazionePerOgniMeseMancante() {
        var contesto = rapporto().con(ordinario(2023, 11), ordinario(2024, 3)).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza).containsExactly(
                new Competenza.Mensilita(2023, 12), new Competenza.Mensilita(2024, 1), new Competenza.Mensilita(2024, 2));
    }

    @Test
    void nessunaSegnalazioneConMesiConsecutivi() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2023, 10), YearMonth.of(2024, 5))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void nessunaSegnalazionePrimaDelPrimoCedolinoAncheConAssunzionePrecedente() {
        var contesto = rapporto().assunzione(LocalDate.of(2020, 1, 1))
                .con(ordinario(2024, 1), ordinario(2024, 2)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void laTredicesimaNonSostituisceDicembre() {
        var contesto = rapporto().con(ordinario(2023, 11), tredicesima(2023), ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(new Competenza.Mensilita(2023, 12));
    }

    @Test
    void estendeFinoAlMeseDiCessazione() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 5, 15))
                .con(ordinario(2024, 2), ordinario(2024, 3)).build();

        var segnalazioni = regola.valuta(contesto);

        assertThat(segnalazioni).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(new Competenza.Mensilita(2024, 4), new Competenza.Mensilita(2024, 5));
        assertThat(segnalazioni.getFirst().descrizione()).contains("la cessazione risulta il 15 Maggio 2024");
        assertThat(segnalazioni.getFirst().evidenze()).extracting(Evidenza::valore).contains("15 Maggio 2024");
    }

    @Test
    void nonEstendeOltreIlMesePrecedenteAOggi() {
        var contesto = rapporto().oggi(LocalDate.of(2024, 5, 20)).cessazione(LocalDate.of(2024, 6, 30))
                .con(ordinario(2024, 2), ordinario(2024, 3)).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(new Competenza.Mensilita(2024, 4));
    }

    @Test
    void cessazioneNelMeseDellUltimoCedolinoNonEstende() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 3, 10))
                .con(ordinario(2024, 2), ordinario(2024, 3), fineRapporto(1, 2024, 4)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void meseCopertoDaUnCedolinoDiFineRapportoNonEMancante() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 4, 30))
                .con(ordinario(2024, 2), ordinario(2024, 3), fineRapporto(1, 2024, 4)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void mesiTraCessazioneERiassunzioneNonSonoMancanti() {
        var contesto = rapporto()
                .con(ordinario(2023, 5), ordinario(2023, 6).cessazione(LocalDate.of(2023, 6, 30)),
                        ordinario(2023, 10).assunzione(LocalDate.of(2023, 10, 1)), ordinario(2023, 11))
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void nessunCedolinoOrdinarioNessunaSegnalazione() {
        var contesto = rapporto().con(tredicesima(2023)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }
}
