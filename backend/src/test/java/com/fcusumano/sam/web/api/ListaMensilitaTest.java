package com.fcusumano.sam.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.web.api.dto.RigaMensilitaDto;
import com.fcusumano.sam.web.api.dto.RigaMensilitaDto.Stato;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListaMensilitaTest {

    @Test
    void laTredicesimaStaSopraDicembreELaQuattordicesimaSopraIlMeseDiPagamento() {
        var righe = ListaMensilita.componi(List.of(
                        DatiDiProva.sintesi(1, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 12)), "1000"),
                        DatiDiProva.sintesi(2, Competenza.Mensilita.tredicesima(2024), "900"),
                        DatiDiProva.sintesi(3, Competenza.Mensilita.ordinaria(YearMonth.of(2025, 1)), "1000"),
                        DatiDiProva.sintesi(4, Competenza.Mensilita.quattordicesima(2024), "900"),
                        DatiDiProva.sintesi(5, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 6)), "1000"),
                        DatiDiProva.sintesi(6, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 5)), "1000")),
                List.of(), List.of(), Map.of(), 5);

        assertThat(righe).extracting(RigaMensilitaDto::titolo).containsExactly("Gennaio 2025", "Tredicesima 2024",
                "Dicembre 2024", "Giugno 2024", "Quattordicesima 2024", "Maggio 2024");
    }

    @Test
    void quattordicesimaPagataADicembreDopoLaTredicesimaEPrimaDelFineRapporto() {
        var righe = ListaMensilita.componi(List.of(
                        DatiDiProva.sintesi(1, new Competenza.FineRapporto(1, YearMonth.of(2024, 12)), "500"),
                        DatiDiProva.sintesi(2, Competenza.Mensilita.quattordicesima(2024), "900"),
                        DatiDiProva.sintesi(3, Competenza.Mensilita.tredicesima(2024), "900"),
                        DatiDiProva.sintesi(4, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 12)), "1000")),
                List.of(), List.of(), Map.of(), 12);

        assertThat(righe).extracting(RigaMensilitaDto::titolo).containsExactly("Fine rapporto 1 – Dicembre 2024",
                "Quattordicesima 2024", "Tredicesima 2024", "Dicembre 2024");
    }

    @Test
    void fineRapportoPerPeriodoEProgressivo() {
        var righe = ListaMensilita.componi(List.of(
                        DatiDiProva.sintesi(1, new Competenza.FineRapporto(1, YearMonth.of(2020, 4)), "500"),
                        DatiDiProva.sintesi(2, new Competenza.FineRapporto(2, YearMonth.of(2020, 4)), "50"),
                        DatiDiProva.sintesi(3, Competenza.Mensilita.ordinaria(YearMonth.of(2020, 4)), "1000"),
                        DatiDiProva.sintesi(4, new Competenza.FineRapporto(3, YearMonth.of(2020, 6)), "20")),
                List.of(), List.of(), Map.of(), null);

        assertThat(righe).extracting(RigaMensilitaDto::titolo).containsExactly("Fine rapporto 3 – Giugno 2020",
                "Fine rapporto 2 – Aprile 2020", "Fine rapporto 1 – Aprile 2020", "Aprile 2020");
        assertThat(righe).extracting(RigaMensilitaDto::chiave)
                .containsExactly("2020.TFR03", "2020.TFR02", "2020.TFR01", "2020.04");
    }

    @Test
    void mancantiSoloSeNonCeGiaUnCedolinoEConSoloMeseEAnno() {
        var righe = ListaMensilita.componi(
                List.of(DatiDiProva.sintesi(1, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 3)), "1000")),
                List.of(Competenza.Mensilita.ordinaria(YearMonth.of(2024, 3)),
                        Competenza.Mensilita.ordinaria(YearMonth.of(2024, 2)),
                        Competenza.Mensilita.tredicesima(2023)),
                List.of(),
                Map.of(new CedolinoId(1), new IndicatoriMensilita(new CedolinoId(1), true, true, null)), null);

        assertThat(righe).extracting(RigaMensilitaDto::titolo)
                .containsExactly("Marzo 2024", "Febbraio 2024", "Tredicesima 2023");
        assertThat(righe).extracting(RigaMensilitaDto::stato).containsExactly(RigaMensilitaDto.Stato.PRESENTE,
                RigaMensilitaDto.Stato.MANCANTE, RigaMensilitaDto.Stato.MANCANTE);
        assertThat(righe.getFirst().scattoInBusta()).isTrue();
        assertThat(righe.getFirst().aumentoFuoriScatti()).isTrue();
        assertThat(righe.get(1).periodoRetribuzione()).isEqualTo(YearMonth.of(2024, 2));
        assertThat(righe.get(2).periodoRetribuzione()).isNull();
        assertThat(righe.get(2).competenza().mese()).isEqualTo(13);
    }

    @Test
    void meseDellaQuattordicesimaNonValidoTrattatoComeNonNoto() {
        var quattordicesima = Competenza.Mensilita.quattordicesima(2024);

        assertThat(ListaMensilita.posizione(quattordicesima, 0))
                .isEqualTo(ListaMensilita.posizione(quattordicesima, null))
                .isEqualTo(ListaMensilita.posizione(quattordicesima, ListaMensilita.MESE_QUATTORDICESIMA_PREDEFINITO));
        assertThat(ListaMensilita.posizione(Competenza.Mensilita.ordinaria(YearMonth.of(2024, 6)), null))
                .isLessThan(ListaMensilita.posizione(quattordicesima, null))
                .isLessThan(ListaMensilita.posizione(Competenza.Mensilita.ordinaria(YearMonth.of(2024, 7)), null));
    }

    @Test
    void precedenzaPresentePoiInErrorePoiMancante() {
        var marzo = Competenza.Mensilita.ordinaria(YearMonth.of(2024, 3));
        var febbraio = Competenza.Mensilita.ordinaria(YearMonth.of(2024, 2));
        var gennaio = Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1));
        var righe = ListaMensilita.componi(
                List.of(DatiDiProva.sintesi(1, marzo, "1000")),
                List.of(marzo, febbraio, gennaio),
                List.of(DatiDiProva.fileInErrore(40, "marzo.pdf", MotivoErrore.PDF_PROTETTO, marzo, true),
                        DatiDiProva.fileInErrore(41, "febbraio.pdf", MotivoErrore.PDF_SENZA_TESTO, febbraio, false)),
                Map.of(), null);

        assertThat(righe).extracting(RigaMensilitaDto::titolo)
                .containsExactly("Marzo 2024", "Febbraio 2024", "Gennaio 2024");
        assertThat(righe).extracting(RigaMensilitaDto::stato)
                .containsExactly(Stato.PRESENTE, Stato.IN_ERRORE, Stato.MANCANTE);
        assertThat(righe.getFirst().cedolinoId()).isEqualTo(1L);
        assertThat(righe.getFirst().caricamentoId()).isNull();
        assertThat(righe.getFirst().motivoErrore()).isNull();
        assertThat(righe.get(1)).satisfies(r -> {
            assertThat(r.chiave()).isEqualTo("2024.02");
            assertThat(r.caricamentoId()).isEqualTo(41L);
            assertThat(r.motivoErrore()).isEqualTo(MotivoErrore.PDF_SENZA_TESTO);
            assertThat(r.periodoRetribuzione()).isEqualTo(YearMonth.of(2024, 2));
            assertThat(r.competenza().mese()).isEqualTo(2);
            assertThat(r.cedolinoId()).isNull();
            assertThat(r.netto()).isNull();
            assertThat(r.lordo()).isNull();
            assertThat(r.nomeFile()).isNull();
            assertThat(r.ratei()).isEmpty();
            assertThat(r.scattoInBusta()).isFalse();
            assertThat(r.aumentoFuoriScatti()).isFalse();
        });
        assertThat(righe.get(2).caricamentoId()).isNull();
        assertThat(righe.get(2).motivoErrore()).isNull();
    }

    @Test
    void inErroreAncheFuoriDalPeriodoAttesoEPerTredicesimaSenzaPeriodo() {
        var righe = ListaMensilita.componi(
                List.of(DatiDiProva.sintesi(1, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 3)), "1000")),
                List.of(),
                List.of(DatiDiProva.fileInErrore(40, "vecchio.pdf", null,
                                Competenza.Mensilita.ordinaria(YearMonth.of(2022, 7)), false),
                        DatiDiProva.fileInErrore(41, "tredicesima.pdf", MotivoErrore.LETTURA_NON_RIUSCITA,
                                Competenza.Mensilita.tredicesima(2023), false),
                        DatiDiProva.fileInErrore(42, "non-associato.pdf", MotivoErrore.LETTURA_NON_RIUSCITA, null,
                                false)),
                Map.of(), null);

        assertThat(righe).extracting(RigaMensilitaDto::titolo)
                .containsExactly("Marzo 2024", "Tredicesima 2023", "Luglio 2022");
        assertThat(righe).extracting(RigaMensilitaDto::caricamentoId).containsExactly(null, 41L, 40L);
        assertThat(righe.get(1).periodoRetribuzione()).isNull();
        assertThat(righe.get(1).competenza().mese()).isEqualTo(13);
        assertThat(righe.get(2).motivoErrore()).as("errore senza motivo registrato").isNull();
        assertThat(righe.get(2).stato()).isEqualTo(Stato.IN_ERRORE);
    }
}
