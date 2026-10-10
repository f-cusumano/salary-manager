package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RegolaScattiInCaloTest {

    private final RegolaScattiInCalo regola = new RegolaScattiInCalo(new BigDecimal("1.00"));

    @Test
    void segnalaNumeroEImportoInCaloInUnaSolaSegnalazione() {
        var contesto = rapporto().con(
                ordinario(2024, 1).scatti(2, "50.00"),
                ordinario(2024, 2).scatti(2, "50.00"),
                ordinario(2024, 3).scatti(1, "25.00")).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.SCATTI_IN_CALO);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 3).build().id());
            assertThat(s.titolo()).isEqualTo("Scatti di anzianità in calo: Marzo 2024");
            assertThat(s.descrizione()).contains("Febbraio 2024", "da 2 a 1", "da 50,00 € a 25,00 €");
            assertThat(s.evidenze()).extracting(Evidenza::valore).containsExactly("2", "1", "50,00 €", "25,00 €");
        });
    }

    @Test
    void segnalaSoloIlNumeroInCalo() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(3, null), ordinario(2024, 2).scatti(2, null)).build();

        assertThat(regola.valuta(contesto)).singleElement()
                .satisfies(s -> assertThat(s.descrizione()).contains("da 3 a 2").doesNotContain("importo"));
    }

    @Test
    void segnalaSoloLImportoInCalo() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(null, "60.00"), ordinario(2024, 2).scatti(null, "40.00"))
                .build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void scattiInAumentoOStabiliNessunaSegnalazione() {
        var contesto = rapporto().con(
                ordinario(2024, 1).scatti(1, "25.00"),
                ordinario(2024, 2).scatti(2, "50.00"),
                ordinario(2024, 3).scatti(2, "50.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cambioDiLivelloEscludeLaSegnalazione() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, "50.00").livello("5"),
                ordinario(2024, 2).scatti(2, "40.00").livello("6")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void livelloNonStampatoNonPermetteDiEscludereIlCambio() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, "50.00").livello(null),
                ordinario(2024, 2).scatti(1, "25.00").livello(null)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cambioDelCcnlStampatoEscludeLaSegnalazione() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, "50.00").ccnl("CCNL Commercio"),
                ordinario(2024, 2).scatti(1, "25.00").ccnl("CCNL Metalmeccanici")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void stessoLivelloScrittoDiversamenteNonEUnCambio() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, null).livello("5 S"),
                ordinario(2024, 2).scatti(1, null).livello("5  s")).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void riduzioneGeneraleDegliElementiNonRiguardaGliScatti() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, "50.00").minimo("2000.00"),
                ordinario(2024, 2).scatti(2, "25.00").minimo("1000.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void caloEntroLaTolleranzaNessunaSegnalazione() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(null, "50.00"), ordinario(2024, 2).scatti(null, "49.50"))
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void confrontaMensilitaConsecutiveAncheConMesiMancanti() {
        var contesto = rapporto().con(ordinario(2024, 1).scatti(2, null), ordinario(2024, 5).scatti(1, null)).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }
}
