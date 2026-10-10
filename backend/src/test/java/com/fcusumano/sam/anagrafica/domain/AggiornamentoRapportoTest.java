package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.DatiRapporto;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AggiornamentoRapportoTest {

    private static final LocalDate ASSUNZIONE = LocalDate.of(2020, 3, 16);
    private static final YearMonth GENNAIO_2024 = YearMonth.of(2024, 1);
    private static final YearMonth MARZO_2024 = YearMonth.of(2024, 3);

    @Test
    void ilPrimoCedolinoValorizzaTutto() {
        var stato = AggiornamentoRapporto.unisci(StatoRapporto.vuoto(),
                dati(ASSUNZIONE, null, " Impiegato ", "5", "0042", GENNAIO_2024), "COMMERCIO");

        assertThat(stato).isEqualTo(new StatoRapporto(ASSUNZIONE, null, "Impiegato", "5", "0042", "COMMERCIO", false,
                GENNAIO_2024));
    }

    @Test
    void unCedolinoPiuRecenteSovrascriveMaINullNonCancellano() {
        var presente = new StatoRapporto(ASSUNZIONE, null, "Impiegato", "4", "0042", "COMMERCIO", false, GENNAIO_2024);

        var stato = AggiornamentoRapporto.unisci(presente, dati(null, null, "Quadro", "Q", null, MARZO_2024), null);

        assertThat(stato).isEqualTo(new StatoRapporto(ASSUNZIONE, null, "Quadro", "Q", "0042", "COMMERCIO", false,
                MARZO_2024));
    }

    @Test
    void unCedolinoPiuVecchioCompletaSoloIValoriMancanti() {
        var presente = new StatoRapporto(null, null, "Quadro", "Q", null, null, false, MARZO_2024);

        var stato = AggiornamentoRapporto.unisci(presente,
                dati(ASSUNZIONE, null, "Impiegato", "4", "0042", GENNAIO_2024), "COMMERCIO");

        assertThat(stato).isEqualTo(new StatoRapporto(ASSUNZIONE, null, "Quadro", "Q", "0042", "COMMERCIO", false,
                MARZO_2024));
    }

    @Test
    void ilCcnlSceltoDallUtenteNonVieneMaiSovrascritto() {
        var presente = new StatoRapporto(null, null, null, null, null, "TURISMO", true, GENNAIO_2024);

        var stato = AggiornamentoRapporto.unisci(presente, dati(null, null, null, null, null, MARZO_2024), "COMMERCIO");

        assertThat(stato.ccnlCodice()).isEqualTo("TURISMO");
        assertThat(stato.ccnlSceltoDaUtente()).isTrue();
    }

    @Test
    void riassunzioneConCessazionePrecedenteScartaLaCessazioneMenoRecente() {
        var presente = new StatoRapporto(LocalDate.of(2018, 1, 1), LocalDate.of(2020, 6, 30), null, null, null, null,
                false, YearMonth.of(2020, 6));

        var stato = AggiornamentoRapporto.unisci(presente,
                dati(LocalDate.of(2022, 2, 1), null, null, null, null, YearMonth.of(2022, 2)), null);

        assertThat(stato.dataAssunzione()).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(stato.dataCessazione()).isNull();
        assertThat(stato.dateCoerenti()).isTrue();
    }

    @Test
    void dateIncoerentiNelloStessoCedolinoScartanoLaCessazione() {
        var stato = AggiornamentoRapporto.unisci(StatoRapporto.vuoto(),
                dati(LocalDate.of(2022, 2, 1), LocalDate.of(2021, 1, 31), null, null, null, GENNAIO_2024), null);

        assertThat(stato.dataAssunzione()).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(stato.dataCessazione()).isNull();
    }

    @Test
    void sovrascriviIgnoraLaRecenzaEAncheINull() {
        var presente = new StatoRapporto(ASSUNZIONE, LocalDate.of(2024, 5, 31), "Quadro", "Q", "0042", "COMMERCIO",
                false, YearMonth.of(2024, 6));

        var stato = AggiornamentoRapporto.sovrascrivi(presente,
                dati(ASSUNZIONE, null, "Impiegato", "4", null, GENNAIO_2024), null);

        assertThat(stato).isEqualTo(new StatoRapporto(ASSUNZIONE, null, "Impiegato", "4", null, null, false,
                GENNAIO_2024));
    }

    @Test
    void sovrascriviMantieneIlCcnlSceltoDallUtente() {
        var presente = new StatoRapporto(null, null, null, null, null, "TURISMO", true, null);

        var stato = AggiornamentoRapporto.sovrascrivi(presente, dati(null, null, null, null, null, null), "COMMERCIO");

        assertThat(stato.ccnlCodice()).isEqualTo("TURISMO");
    }

    private static DatiRapporto dati(LocalDate assunzione, LocalDate cessazione, String qualifica, String livello,
                                     String codiceDipendente, YearMonth riferimento) {
        return new DatiRapporto(assunzione, cessazione, qualifica, livello, codiceDipendente, null, riferimento);
    }
}
