package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.tredicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RegolaProgressiviInCaloTest {

    private final RegolaProgressiviInCalo regola = new RegolaProgressiviInCalo(new BigDecimal("1.00"));

    @Test
    void segnalaIProgressiviInCaloNelloStessoAnno() {
        var contesto = rapporto().con(
                ordinario(2024, 1).progressivi("2300.00", "2100.00", "400.00"),
                ordinario(2024, 2).progressivi("4600.00", "4200.00", "800.00"),
                ordinario(2024, 3).progressivi("4000.00", "3900.00", "800.00")).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.PROGRESSIVI_IN_CALO);
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 3).build().id());
            assertThat(s.titolo()).isEqualTo("Progressivi annui in calo: Marzo 2024");
            assertThat(s.descrizione()).contains(
                    "imponibile previdenziale da 4.600,00 € a 4.000,00 €",
                    "imponibile IRPEF da 4.200,00 € a 3.900,00 €").doesNotContain("IRPEF trattenuta");
            assertThat(s.evidenze()).hasSize(4);
        });
    }

    @Test
    void gennaioRipartedaZero() {
        var contesto = rapporto().con(ordinario(2023, 12).progressivi("30000.00", "27000.00", "5000.00"),
                ordinario(2024, 1).progressivi("2300.00", "2100.00", "400.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void progressiviCrescentiNessunaSegnalazione() {
        var contesto = rapporto().con(ordinario(2024, 1).progressivi("2300.00", "2100.00", "400.00"),
                ordinario(2024, 2).progressivi("4600.00", "4200.00", "800.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void caloEntroLaTolleranzaNessunaSegnalazione() {
        var contesto = rapporto().con(ordinario(2024, 1).progressivi("2300.00", null, null),
                ordinario(2024, 2).progressivi("2299.50", null, null)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void conguaglioDiDicembreGiustificaIlCaloDellIrpef() {
        var contesto = rapporto().con(ordinario(2024, 11).progressivi("25000.00", "23000.00", "4500.00"),
                ordinario(2024, 12).progressivi("27300.00", "25100.00", "4300.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void conguaglioNonGiustificaIlCaloDellImponibilePrevidenziale() {
        var contesto = rapporto().con(ordinario(2024, 11).progressivi("25000.00", "23000.00", "4500.00"),
                ordinario(2024, 12).progressivi("24000.00", "25100.00", "4300.00")).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> assertThat(s.descrizione())
                .contains("imponibile previdenziale").doesNotContain("IRPEF trattenuta"));
    }

    @Test
    void conguaglio730GiustificaIlCaloDellIrpef() {
        var contesto = rapporto().con(ordinario(2024, 6).progressivi("13800.00", "12600.00", "2400.00"),
                ordinario(2024, 7).progressivi("16100.00", "14700.00", "2100.00")
                        .voce("Rimborso da 730", CategoriaVoce.TRATTENUTA_FISCALE, "700.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void irpefInCaloSenzaConguaglioSegnala() {
        var contesto = rapporto().con(ordinario(2024, 6).progressivi("13800.00", "12600.00", "2400.00"),
                ordinario(2024, 7).progressivi("16100.00", "14700.00", "2100.00")).build();

        assertThat(regola.valuta(contesto)).singleElement()
                .satisfies(s -> assertThat(s.descrizione()).contains("IRPEF trattenuta da 2.400,00 € a 2.100,00 €"));
    }

    @Test
    void conguaglioNelMeseDiCessazioneGiustificaIlCaloDellIrpef() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 7, 15))
                .con(ordinario(2024, 6).progressivi("13800.00", "12600.00", "2400.00"),
                        ordinario(2024, 7).progressivi("15000.00", "13700.00", "2000.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cedolinoDiFineRapportoConfrontatoConLUltimaMensilita() {
        var contesto = rapporto().cessazione(LocalDate.of(2024, 6, 15))
                .con(ordinario(2024, 6).progressivi("12000.00", "11000.00", "2000.00"),
                        fineRapporto(1, 2024, 7).progressivi("11000.00", "10500.00", "1500.00")).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.titolo()).isEqualTo("Progressivi annui in calo: Fine rapporto 1 – Luglio 2024");
            assertThat(s.descrizione()).contains("imponibile previdenziale").doesNotContain("imponibile IRPEF");
        });
    }

    @Test
    void tredicesimaElaborataPrimaDiDicembreNonEConfrontata() {
        var contesto = rapporto().con(
                ordinario(2024, 11).progressivi("25000.00", "23000.00", "4500.00"),
                tredicesima(2024).progressivi("27000.00", "24800.00", "4900.00"),
                ordinario(2024, 12).progressivi("29300.00", "26900.00", "5300.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void progressiviNonStampatiNonSonoConfrontabili() {
        var contesto = rapporto().con(ordinario(2024, 1).progressivi("2300.00", null, null),
                ordinario(2024, 2).progressivi(null, "1000.00", null)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }
}
