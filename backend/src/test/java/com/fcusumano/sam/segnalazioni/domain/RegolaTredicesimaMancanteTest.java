package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinari;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.tredicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegolaTredicesimaMancanteTest {

    private final RegolaTredicesimaMancante regola = new RegolaTredicesimaMancante(Set.of("EDILIZIA_INDUSTRIA"));

    private static ContestoDiProva anno2023ConGennaio2024() {
        return rapporto().con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2024, 1)));
    }

    @Test
    void segnalaLaTredicesimaMancanteDiUnAnnoConcluso() {
        var segnalazioni = regola.valuta(anno2023ConGennaio2024().build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.TREDICESIMA_MANCANTE);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.titolo()).isEqualTo("Tredicesima 2023 mancante");
            assertThat(s.descrizione()).contains("2023", "successivi a Dicembre 2023");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione)
                    .containsExactly("Cedolino di dicembre: Dicembre 2023", "Primo cedolino successivo: Gennaio 2024");
        });
    }

    @Test
    void annoNonConclusoNessunaSegnalazione() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 12))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void dicembreNonCaricatoSenzaIndiziSegnalaUnAvviso() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 11)))
                .con(ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.titolo()).isEqualTo("Tredicesima 2023 non trovata");
            assertThat(s.descrizione()).contains("Dicembre 2023 non è stato caricato", "potrebbe essere pagata");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione).containsExactly(
                    "Ultimo cedolino prima di dicembre: Novembre 2023", "Primo cedolino successivo: Gennaio 2024");
        });
    }

    @Test
    void dicembreNonCaricatoConTredicesimaAParteNegliAltriAnniEUnAnomalia() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2022, 1), YearMonth.of(2023, 11)))
                .con(tredicesima(2022), ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.titolo()).isEqualTo("Tredicesima 2023 mancante");
            assertThat(s.descrizione()).contains("cedolino a parte", "Dicembre 2023 non è stato caricato");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione)
                    .contains("Tredicesima con cedolino a parte: Tredicesima 2022");
        });
    }

    @Test
    void dicembreNonCaricatoConTredicesimaNelCedolinoDiDicembreNegliAltriAnniNessunaSegnalazione() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2022, 1), YearMonth.of(2023, 11)))
                .sostituisci(ordinario(2022, 12).conTredicesima())
                .con(ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void dicembreConUnFileInErroreAssociatoSegnalaLaTredicesima() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 11)))
                .con(ordinario(2024, 1))
                .inErrore(new Competenza.Mensilita(2023, 12), MotivoErrore.PDF_SENZA_TESTO).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.descrizione()).contains("Dicembre 2023 è stato caricato con errore");
        });
    }

    @Test
    void tredicesimaAssociataAUnFileInErroreInUnAltroAnnoIndicaIlCedolinoAParte() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2022, 1), YearMonth.of(2023, 11)))
                .con(ordinario(2024, 1))
                .inErrore(Competenza.Mensilita.tredicesima(2022), MotivoErrore.PDF_SENZA_TESTO).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
        });
    }

    @Test
    void annoSenzaCedoliniOrdinariCaricatiSiValutaComunque() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2022, 1), YearMonth.of(2022, 12)))
                .con(tredicesima(2022), ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.competenza()).isEqualTo(Competenza.Mensilita.tredicesima(2023));
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
        });
    }

    @Test
    void dicembreNonAttesoNessunaSegnalazione() {
        // rapporto interrotto da dicembre 2023 a gennaio 2024: dicembre non e' una mensilita' mancante
        var contesto = rapporto().con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 11)))
                .sostituisci(ordinario(2023, 11).cessazione(LocalDate.of(2023, 11, 30)))
                .con(ordinario(2024, 2).assunzione(LocalDate.of(2024, 2, 1))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cedolinoDiTredicesimaPresente() {
        var contesto = anno2023ConGennaio2024().con(tredicesima(2023)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void tredicesimaPagataNelCedolinoDiDicembre() {
        var contesto = anno2023ConGennaio2024().sostituisci(ordinario(2023, 12).conTredicesima()).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void tredicesimaPagataNelCedolinoDiGennaioSuccessivo() {
        var contesto = anno2023ConGennaio2024().sostituisci(ordinario(2024, 1).conTredicesima()).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cessatoNellAnnoSenzaCedolinoFinaleLoLasciaAFineRapportoMancante() {
        var contesto = rapporto().cessazione(LocalDate.of(2023, 6, 15))
                .con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 6))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cessatoNellAnnoConCedolinoFinaleSenzaRateiSegnalaConDescrizioneSpecifica() {
        var contesto = rapporto().cessazione(LocalDate.of(2023, 6, 15))
                .con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 6)))
                .con(fineRapporto(1, 2023, 7))
                .build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.titolo()).isEqualTo("Ratei di tredicesima 2023 non liquidati");
            assertThat(s.competenza()).isNull();
            assertThat(s.cedolinoId()).isEqualTo(fineRapporto(1, 2023, 7).build().id());
            assertThat(s.descrizione()).contains("cessato il 15 Giugno 2023");
        });
    }

    @Test
    void cessatoConTfrLiquidatoNelMeseDiCessazioneSenzaRateiSegnala() {
        var tfrLiquidato = new DatiTfr(null, null, null, null, null, null, null, null, null, new BigDecimal("5000.00"),
                null);
        var contesto = rapporto().cessazione(LocalDate.of(2023, 6, 15))
                .con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 5)))
                .con(ordinario(2023, 6).tfr(tfrLiquidato))
                .build();

        assertThat(regola.valuta(contesto)).singleElement()
                .satisfies(s -> assertThat(s.titolo()).startsWith("Ratei di tredicesima 2023"));
    }

    @Test
    void cessatoConRateiNelCedolinoFinaleNessunaSegnalazione() {
        var contesto = rapporto().cessazione(LocalDate.of(2023, 6, 15))
                .con(ordinari(YearMonth.of(2023, 1), YearMonth.of(2023, 6)))
                .con(fineRapporto(1, 2023, 7).conTredicesima())
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cessatoDopoMenoDiQuindiciGiorniNellAnnoNonMaturaRatei() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 1, 10))
                .con(ordinari(YearMonth.of(2023, 11), YearMonth.of(2023, 12)))
                .sostituisci(ordinario(2023, 12).conTredicesima())
                .con(ordinario(2024, 1), fineRapporto(1, 2024, 2))
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void assuntoNegliUltimiGiorniDellAnnoNonMaturaRatei() {
        var contesto = rapporto().assunzione(LocalDate.of(2023, 12, 20))
                .con(ordinario(2023, 12), ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void senzaAssunzionePrimoCedolinoDiDicembreNonSiValutaLAnno() {
        var contesto = rapporto().con(ordinario(2023, 12), ordinario(2024, 1), ordinario(2024, 2)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void senzaAssunzionePrimoCedolinoDiNovembreSegnala() {
        var contesto = rapporto().con(ordinario(2023, 11), ordinario(2023, 12), ordinario(2024, 1)).build();

        assertThat(regola.valuta(contesto)).singleElement()
                .satisfies(s -> assertThat(s.titolo()).isEqualTo("Tredicesima 2023 mancante"));
    }

    @Test
    void ediliziaOperaioTredicesimaInCassaEdile() {
        var contesto = anno2023ConGennaio2024().ccnl(ContestoDiProva.EDILIZIA).qualifica("Operaio").build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void ediliziaImpiegatoSegnala() {
        var contesto = anno2023ConGennaio2024().ccnl(ContestoDiProva.EDILIZIA).qualifica("Impiegato tecnico").build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void rapportoProseguitoDopoUnaCessazioneStampataSiValutaNormalmente() {
        var contesto = anno2023ConGennaio2024().cessazione(LocalDate.of(2023, 3, 31)).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(Competenza.Mensilita.tredicesima(2023));
    }

    @Test
    void tredicesimaConUnFileInErroreAssociatoNonEMancante() {
        var contesto = anno2023ConGennaio2024()
                .inErrore(Competenza.Mensilita.tredicesima(2023), MotivoErrore.PDF_SENZA_TESTO).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void unFileInErroreAssociatoAUnAltroAnnoNonCambiaNulla() {
        var contesto = anno2023ConGennaio2024()
                .inErrore(Competenza.Mensilita.tredicesima(2022), MotivoErrore.PDF_SENZA_TESTO)
                .inErrore(new Competenza.Mensilita(2023, 12), MotivoErrore.PDF_SENZA_TESTO).build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::competenza)
                .containsExactly(Competenza.Mensilita.tredicesima(2023));
    }
}
