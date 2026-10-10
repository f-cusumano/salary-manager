package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.METALMECCANICO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RegolaScattoNonInBustaTest {

    private static final LocalDate SCATTO = LocalDate.of(2024, 9, 1);

    private final RegolaScattoNonInBusta regola = new RegolaScattoNonInBusta();

    /** Agosto con 1 scatto e prossimo scatto a settembre; ottobre con i valori indicati. */
    private static ContestoDiProva scatto(Integer numeroOttobre, String importoOttobre) {
        return rapporto().con(
                ordinario(2024, 7).scatti(1, "25.00").prossimoScatto(SCATTO),
                ordinario(2024, 8).scatti(1, "25.00").prossimoScatto(SCATTO),
                ordinario(2024, 9).scatti(1, "25.00").prossimoScatto(SCATTO),
                ordinario(2024, 10).scatti(numeroOttobre, importoOttobre).prossimoScatto(SCATTO));
    }

    @Test
    void segnalaLoScattoAttesoNonPresenteInBusta() {
        var segnalazioni = regola.valuta(scatto(1, "25.00").build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.SCATTO_NON_IN_BUSTA);
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 10).build().id());
            assertThat(s.titolo()).isEqualTo("Scatto di anzianità non in busta: Ottobre 2024");
            assertThat(s.descrizione()).contains("Il cedolino di Agosto 2024", "al 1 Settembre 2024",
                    "nel cedolino di Ottobre 2024", "rispetto al cedolino di Agosto 2024", "numero di scatti 1 e 1",
                    "importo mensile 25,00 € e 25,00 €");
            assertThat(s.evidenze()).extracting(Evidenza::valore)
                    .containsExactly("1 Settembre 2024", "1", "1", "25,00 €", "25,00 €");
        });
    }

    @Test
    void scattoPagatoNessunaSegnalazione() {
        assertThat(regola.valuta(scatto(2, "50.00").build())).isEmpty();
    }

    @Test
    void aumentoDelSoloImportoBasta() {
        assertThat(regola.valuta(scatto(null, "50.00").build())).isEmpty();
    }

    @Test
    void scattoPagatoNelMeseStessoNessunaSegnalazione() {
        var contesto = scatto(2, "50.00").sostituisci(ordinario(2024, 9).scatti(2, "50.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void valoriNonConfrontabiliNessunaSegnalazione() {
        var contesto = rapporto().con(
                ordinario(2024, 8).scatti(null, null).prossimoScatto(SCATTO),
                ordinario(2024, 10).scatti(1, "25.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void massimoGiaRaggiuntoSecondoIlCcnlNessunaSegnalazione() {
        var contesto = rapporto().ccnl(METALMECCANICO).con(
                ordinario(2024, 8).scatti(5, "125.00").prossimoScatto(SCATTO),
                ordinario(2024, 10).scatti(5, "125.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void sottoIlMassimoDelCcnlSegnala() {
        var contesto = rapporto().ccnl(METALMECCANICO).con(
                ordinario(2024, 8).scatti(3, "75.00").prossimoScatto(SCATTO),
                ordinario(2024, 10).scatti(3, "75.00")).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void scattoRinviatoDalSoftwarePagheNessunaSegnalazione() {
        var contesto = scatto(1, "25.00")
                .sostituisci(ordinario(2024, 9).scatti(1, "25.00").prossimoScatto(LocalDate.of(2024, 12, 1)))
                .sostituisci(ordinario(2024, 10).scatti(1, "25.00").prossimoScatto(LocalDate.of(2024, 12, 1)))
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void senzaCedolinoDiVerificaNessunaSegnalazione() {
        var contesto = rapporto().con(
                ordinario(2024, 8).scatti(1, "25.00").prossimoScatto(SCATTO),
                ordinario(2024, 9).scatti(1, "25.00").prossimoScatto(SCATTO)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void senzaCedolinoPrecedenteAlloScattoNessunaSegnalazione() {
        var contesto = rapporto().con(
                ordinario(2024, 9).scatti(1, "25.00").prossimoScatto(SCATTO),
                ordinario(2024, 10).scatti(1, "25.00")).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void scattiInCaloLasciatiASegnalareAScattiInCalo() {
        assertThat(regola.valuta(scatto(0, "0.00").build())).isEmpty();
    }
}
