package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.COMMERCIO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.METALMECCANICO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.TELECOMUNICAZIONI;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegolaLivelloInCaloTest {

    private final RegolaLivelloInCalo regola = new RegolaLivelloInCalo();

    private static ContestoRapporto livelli(Ccnl ccnl, String prima, String dopo) {
        return rapporto().ccnl(ccnl).con(ordinario(2024, 1).livello(prima), ordinario(2024, 2).livello(dopo)).build();
    }

    @Test
    void segnalaIlLivelloInCaloPerIlCommercio() {
        var segnalazioni = regola.valuta(livelli(COMMERCIO, "3", "4"));

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.LIVELLO_IN_CALO);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 2).build().id());
            assertThat(s.titolo()).isEqualTo("Livello di inquadramento in calo: Febbraio 2024");
            assertThat(s.descrizione()).contains("da 3 (Gennaio 2024) a 4 (Febbraio 2024)",
                    "un numero più basso indica un livello più alto");
            assertThat(s.evidenze()).extracting(Evidenza::valore).containsExactly("3", "4");
        });
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "COMMERCIO, Quadro, 1",
            "COMMERCIO, 1° livello, 2",
            "METALMECCANICO, 5, 4",
            "METALMECCANICO, 5S, 5",
            "METALMECCANICO, 5' Livello, 3",
            "METALMECCANICO, C3, C2",
            "METALMECCANICO, A1, B3",
            "METALMECCANICO, C1, D2"
    })
    void cali(String ccnl, String prima, String dopo) {
        assertThat(regola.valuta(livelli(ccnl(ccnl), prima, dopo))).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "COMMERCIO, 4, 3",
            "COMMERCIO, 1, Quadro",
            "COMMERCIO, 3, 3",
            "METALMECCANICO, 4, 5",
            "METALMECCANICO, 5, 5S",
            "METALMECCANICO, C2, C3",
            "METALMECCANICO, D2, C1",
            "METALMECCANICO, 6, C3",
            "METALMECCANICO, C3, 4",
            "COMMERCIO, 3, impiegato",
            "COMMERCIO, 8, 9"
    })
    void nessunCaloOConfrontoNonCerto(String ccnl, String prima, String dopo) {
        assertThat(regola.valuta(livelli(ccnl(ccnl), prima, dopo))).isEmpty();
    }

    @Test
    void ccnlSenzaScalaCertaNessunaSegnalazione() {
        assertThat(regola.valuta(livelli(TELECOMUNICAZIONI, "3", "4"))).isEmpty();
        assertThat(regola.valuta(livelli(TELECOMUNICAZIONI, "4", "3"))).isEmpty();
    }

    @Test
    void ccnlNonNotoNessunaSegnalazione() {
        assertThat(regola.valuta(livelli(null, "3", "4"))).isEmpty();
    }

    @Test
    void cambioDelCcnlStampatoNessunaSegnalazione() {
        var contesto = rapporto().ccnl(COMMERCIO).con(ordinario(2024, 1).livello("3").ccnl("Commercio"),
                ordinario(2024, 2).livello("4").ccnl("Turismo")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void livelloNonStampatoNessunaSegnalazione() {
        assertThat(regola.valuta(livelli(COMMERCIO, "3", null))).isEmpty();
    }

    private static Ccnl ccnl(String nome) {
        return switch (nome) {
            case "COMMERCIO" -> COMMERCIO;
            case "METALMECCANICO" -> METALMECCANICO;
            default -> throw new IllegalArgumentException(nome);
        };
    }
}
