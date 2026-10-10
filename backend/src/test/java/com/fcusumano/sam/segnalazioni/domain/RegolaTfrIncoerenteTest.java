package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.tredicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Valori inventati ma coerenti: fondo al 31/12/2022 = 2.410,35; rivalutazione 50,62; imposta 8,61; quota 2023 =
 * 2.073,96 (ultimo progressivo, sulla tredicesima elaborata dopo dicembre) => fondo atteso al 31/12/2023 = 4.526,32.
 */
class RegolaTfrIncoerenteTest {

    private static final String FONDO_2022 = "2410.35";
    private static final String FONDO_2023_COERENTE = "4526.32";

    private final RegolaTfrIncoerente regola = new RegolaTfrIncoerente(new BigDecimal("5.00"), new BigDecimal("1.00"));

    private static ContestoDiProva anno2023(String fondo2023) {
        var contesto = rapporto().assunzione(LocalDate.of(2021, 9, 1));
        for (int mese = 1; mese <= 11; mese++) {
            contesto.con(ordinario(2023, mese).tfr(FONDO_2022, null, null, String.valueOf(160 * mese)));
        }
        contesto.con(ordinario(2023, 12).tfr(FONDO_2022, "50.62", "8.61", "1913.36"));
        contesto.con(tredicesima(2023).tfr(FONDO_2022, null, null, "2073.96"));
        return contesto.con(ordinario(2024, 1).tfr(fondo2023, null, null, "160.60"));
    }

    private static DatiTfr tfr(String fondo, String rivalutazione, String imposta, String quota, String quotaFondi,
                               String anticipi) {
        return new DatiTfr(euro(fondo), euro(rivalutazione), euro(imposta), euro(quota), null, euro(quotaFondi), null,
                euro(anticipi), null, null, null);
    }

    @Test
    void fondoCoerenteNessunaSegnalazione() {
        assertThat(regola.valuta(anno2023(FONDO_2023_COERENTE).build())).isEmpty();
    }

    @Test
    void scostamentoEntroLaTolleranzaNessunaSegnalazione() {
        assertThat(regola.valuta(anno2023("4530.00").build())).isEmpty();
    }

    @Test
    void segnalaIlFondoIncoerente() {
        var segnalazioni = regola.valuta(anno2023("4700.00").build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.TFR_INCOERENTE);
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 1).build().id());
            assertThat(s.titolo()).isEqualTo("Fondo TFR al 31/12/2023 incoerente");
            assertThat(s.descrizione()).contains("4.700,00 €", "differisce di 173,68 €", "atteso (4.526,32 €",
                    "fondo al 31/12/2022 2.410,35 €", "rivalutazione 50,62 €", "imposta sostitutiva 8,61 €",
                    "quota TFR del 2023 2.073,96 €").doesNotContain("anticipi");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione).containsExactly(
                    "Fondo TFR al 31/12/2022 (Tredicesima 2023)", "Rivalutazione (Dicembre 2023)",
                    "Imposta sostitutiva (Dicembre 2023)", "Quota TFR del 2023 (Tredicesima 2023)",
                    "Fondo TFR al 31/12/2023 (Gennaio 2024)", "Fondo TFR atteso al 31/12/2023");
        });
    }

    @Test
    void fondoNonAncoraAggiornatoNessunaSegnalazione() {
        assertThat(regola.valuta(anno2023(FONDO_2022).build())).isEmpty();
    }

    @Test
    void dicembreMancanteNessunaSegnalazione() {
        var contesto = rapporto().assunzione(LocalDate.of(2021, 9, 1));
        for (int mese = 1; mese <= 11; mese++) {
            contesto.con(ordinario(2023, mese).tfr(FONDO_2022, null, null, String.valueOf(160 * mese)));
        }
        contesto.con(ordinario(2024, 1).tfr("4700.00", null, null, "160.60"));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void rivalutazioneNonStampataConFondoPositivoNessunaSegnalazione() {
        var contesto = anno2023("4700.00").sostituisci(ordinario(2023, 12).tfr(FONDO_2022, null, null, "1913.36"));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void anticipoProgressivoEntraNelCalcolo() {
        var contesto = anno2023("3526.32");
        for (int mese = 6; mese <= 11; mese++) {
            contesto.sostituisci(ordinario(2023, mese).tfr(tfr(FONDO_2022, null, null, String.valueOf(160 * mese),
                    null, "1000.00")));
        }
        contesto.sostituisci(ordinario(2023, 12).tfr(tfr(FONDO_2022, "50.62", "8.61", "1913.36", null, "1000.00")));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void anticipoNonConsideratoDalFondoSegnala() {
        var contesto = anno2023(FONDO_2023_COERENTE);
        contesto.sostituisci(ordinario(2023, 12).tfr(tfr(FONDO_2022, "50.62", "8.61", "1913.36", null, "1000.00")));

        assertThat(regola.valuta(contesto.build())).singleElement()
                .satisfies(s -> assertThat(s.descrizione()).contains("- anticipi 1.000,00 €"));
    }

    @Test
    void anticipiDiInterpretazioneAmbiguaNessunaSegnalazione() {
        var contesto = anno2023("4700.00");
        contesto.sostituisci(ordinario(2023, 3).tfr(tfr(FONDO_2022, null, null, "480", null, "500.00")));
        contesto.sostituisci(ordinario(2023, 7).tfr(tfr(FONDO_2022, null, null, "1120", null, "300.00")));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void quoteAFondiMensiliCostantiSonoAmbigueNessunaSegnalazione() {
        var contesto = anno2023("4700.00");
        for (int mese = 1; mese <= 11; mese++) {
            contesto.sostituisci(ordinario(2023, mese).tfr(tfr(FONDO_2022, null, null, String.valueOf(160 * mese),
                    "50.00", null)));
        }
        contesto.sostituisci(ordinario(2023, 12).tfr(tfr(FONDO_2022, "50.62", "8.61", "1913.36", "50.00", null)));

        assertThat(regola.valuta(contesto.build())).isEmpty();
    }

    @Test
    void caloGiaSegnalatoDaFondoTfrInCaloNonSiRipete() {
        assertThat(regola.valuta(anno2023("2000.00").build())).isEmpty();
    }

    @Test
    void caloConAnticipoNonCoerenteSegnala() {
        var contesto = anno2023("2000.00");
        contesto.sostituisci(ordinario(2023, 12).tfr(tfr(FONDO_2022, "50.62", "8.61", "1913.36", null, "1000.00")));

        assertThat(regola.valuta(contesto.build())).hasSize(1);
    }

    @Test
    void annoConCessazioneOLiquidazioneNessunaSegnalazione() {
        assertThat(regola.valuta(anno2023("4700.00").cessazione(LocalDate.of(2023, 12, 31)).build())).isEmpty();
        assertThat(regola.valuta(anno2023("4700.00").con(fineRapporto(1, 2023, 12)).build())).isEmpty();
    }

    @Test
    void assuntoNellAnnoConFondoInizialeZeroSenzaRivalutazione() {
        var contesto = rapporto().assunzione(LocalDate.of(2023, 11, 1))
                .con(ordinario(2023, 11).tfr("0", null, null, "150.00"),
                        ordinario(2023, 12).tfr("0", null, null, "300.00"),
                        ordinario(2024, 1).tfr("300.00", null, null, "150.00"));
        assertThat(regola.valuta(contesto.build())).isEmpty();

        contesto.sostituisci(ordinario(2024, 1).tfr("450.00", null, null, "150.00"));
        assertThat(regola.valuta(contesto.build())).hasSize(1);
    }
}
