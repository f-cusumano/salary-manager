package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RegolaFondoTfrInCaloTest {

    private final RegolaFondoTfrInCalo regola = new RegolaFondoTfrInCalo(new BigDecimal("1.00"));

    /** Anno 2023 completo con fondo al 31/12/2022 = 5.000,00 e gennaio 2024 con il fondo indicato. */
    private static ContestoDiProva fondi(String fondo2023) {
        var contesto = rapporto().assunzione(LocalDate.of(2018, 1, 1));
        for (int mese = 1; mese <= 12; mese++) {
            contesto.con(ordinario(2023, mese).tfr("5000.00", null, null, null));
        }
        return contesto.con(ordinario(2024, 1).tfr(fondo2023, null, null, null));
    }

    @Test
    void segnalaIlCaloDelFondoTraDueAnni() {
        var segnalazioni = regola.valuta(fondi("4000.00").build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.FONDO_TFR_IN_CALO);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 1).build().id());
            assertThat(s.titolo()).isEqualTo("Fondo TFR in calo al 31/12/2023");
            assertThat(s.descrizione()).contains("4.000,00 €", "5.000,00 €", "di 1.000,00 €", "31/12/2022");
            assertThat(s.evidenze()).extracting(Evidenza::valore).containsExactly("5.000,00 €", "4.000,00 €");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione).containsExactly(
                    "Fondo TFR al 31/12/2022 (Dicembre 2023)", "Fondo TFR al 31/12/2023 (Gennaio 2024)");
        });
    }

    @Test
    void fondoInCrescitaNessunaSegnalazione() {
        assertThat(regola.valuta(fondi("6500.00").build())).isEmpty();
    }

    @Test
    void caloEntroLaTolleranzaNessunaSegnalazione() {
        assertThat(regola.valuta(fondi("4999.50").build())).isEmpty();
    }

    @Test
    void annoConAnticipoNessunaSegnalazione() {
        var anticipo = new DatiTfr(euro("5000.00"), null, null, null, null, null, null, euro("2000.00"), null, null,
                null);
        assertThat(regola.valuta(fondi("4000.00").sostituisci(ordinario(2023, 6).tfr(anticipo)).build())).isEmpty();
    }

    @Test
    void annoConQuoteAFondiNessunaSegnalazione() {
        var fondo = new DatiTfr(euro("5000.00"), null, null, null, null, euro("1500.00"), null, null, null, null, null);
        assertThat(regola.valuta(fondi("4000.00").sostituisci(ordinario(2023, 12).tfr(fondo)).build())).isEmpty();
    }

    @Test
    void annoConLiquidazioneNessunaSegnalazione() {
        var liquidazione = new DatiTfr(euro("5000.00"), null, null, null, null, null, null, null, null,
                euro("3000.00"), null);
        assertThat(regola.valuta(fondi("4000.00").sostituisci(ordinario(2023, 9).tfr(liquidazione)).build()))
                .isEmpty();
    }

    @Test
    void annoConCessazioneNessunaSegnalazione() {
        assertThat(regola.valuta(fondi("4000.00").cessazione(LocalDate.of(2023, 12, 31)).build())).isEmpty();
    }

    @Test
    void annoConMensilitaMancanteNessunaSegnalazione() {
        var contesto = rapporto().assunzione(LocalDate.of(2018, 1, 1));
        for (int mese = 1; mese <= 12; mese++) {
            if (mese != 5) {
                contesto.con(ordinario(2023, mese).tfr("5000.00", null, null, null));
            }
        }
        contesto.con(ordinario(2024, 1).tfr("4000.00", null, null, null));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void assuntoInCorsoDAnnoBastanoIMesiDallAssunzione() {
        var contesto = rapporto().assunzione(LocalDate.of(2023, 10, 1))
                .con(ordinario(2023, 10).tfr("800.00", null, null, null),
                        ordinario(2023, 11).tfr("800.00", null, null, null),
                        ordinario(2023, 12).tfr("800.00", null, null, null),
                        ordinario(2024, 1).tfr("500.00", null, null, null));

        assertThat(regola.valuta(contesto.build())).hasSize(1);
    }

    @Test
    void fondoDellAnnoSuccessivoNonStampatoNessunaSegnalazione() {
        assertThat(regola.valuta(fondi(null).build())).isEmpty();
    }
}
