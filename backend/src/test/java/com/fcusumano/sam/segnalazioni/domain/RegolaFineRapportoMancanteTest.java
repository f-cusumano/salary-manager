package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinari;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RegolaFineRapportoMancanteTest {

    private static final LocalDate CESSAZIONE = LocalDate.of(2024, 3, 15);

    private final RegolaFineRapportoMancante regola = new RegolaFineRapportoMancante(2);

    private static ContestoDiProva cessatoAMarzo() {
        return rapporto().cessazione(CESSAZIONE).oggi(LocalDate.of(2024, 10, 1))
                .con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 3)));
    }

    @Test
    void segnalaCessazioneSenzaCedolinoFinale() {
        var segnalazioni = regola.valuta(cessatoAMarzo().build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.FINE_RAPPORTO_MANCANTE);
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.cedolinoId()).isEqualTo(ordinario(2024, 3).build().id());
            assertThat(s.competenza()).isNull();
            assertThat(s.titolo()).isEqualTo("Cedolino di fine rapporto mancante");
            assertThat(s.descrizione()).contains("cessato il 15 Marzo 2024");
            assertThat(s.evidenze()).extracting(Evidenza::valore).contains("15 Marzo 2024");
        });
    }

    @Test
    void senzaDataDiCessazioneNessunaSegnalazione() {
        var contesto = rapporto().con(ordinari(YearMonth.of(2024, 1), YearMonth.of(2024, 3))).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void cessazioneTroppoRecenteNessunaSegnalazione() {
        assertThat(regola.valuta(cessatoAMarzo().oggi(LocalDate.of(2024, 5, 14)).build())).isEmpty();
        assertThat(regola.valuta(cessatoAMarzo().oggi(LocalDate.of(2024, 5, 15)).build())).hasSize(1);
    }

    @Test
    void cedolinoDiFineRapportoPresente() {
        assertThat(regola.valuta(cessatoAMarzo().con(fineRapporto(1, 2024, 5)).build())).isEmpty();
    }

    @Test
    void tfrLiquidatoNelCedolinoDelMeseDiCessazione() {
        var liquidato = new DatiTfr(null, null, null, null, null, null, null, null, null, euro("8000.00"), null);
        var contesto = cessatoAMarzo().sostituisci(ordinario(2024, 3).tfr(liquidato)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void tfrLiquidatoPrimaDellaCessazioneNonBasta() {
        var liquidato = new DatiTfr(null, null, null, null, null, null, null, null, null, euro("8000.00"), null);
        var contesto = cessatoAMarzo().sostituisci(ordinario(2024, 1).tfr(liquidato)).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }

    @Test
    void rapportoProseguitoDopoLaCessazioneStampata() {
        var contesto = cessatoAMarzo().con(ordinario(2024, 4), ordinario(2024, 5)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void tfrInteramenteVersatoAUnFondoPensione() {
        var aFondo = new DatiTfr(euro("0"), null, null, euro("300.00"), euro("100.00"), euro("300.00"), null, null,
                null, null, null);
        var contesto = cessatoAMarzo().sostituisci(ordinario(2024, 3).tfr(aFondo)).build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void tfrInAziendaConQuoteParzialiAFondoSegnala() {
        var misto = new DatiTfr(euro("12000.00"), null, null, euro("600.00"), euro("200.00"), euro("300.00"), null,
                null, null, null, null);
        var contesto = cessatoAMarzo().sostituisci(ordinario(2024, 3).tfr(misto)).build();

        assertThat(regola.valuta(contesto)).hasSize(1);
    }
}
