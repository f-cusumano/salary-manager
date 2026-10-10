package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloAnzianitaTest {

    private static CedoliniOrdinati cedolini(CedolinoDiProva... cedolini) {
        return CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList());
    }

    @Test
    void daGennaio2024AFebbraio2026SonoDueAnniEDueMesi() {
        var anzianita = CalcoloAnzianita.calcola(
                contesto(LocalDate.of(2024, 1, 15), LocalDate.of(2026, 2, 10), null), cedolini(ordinario(2024, 1)));

        assertThat(anzianita.anni()).isEqualTo(2);
        assertThat(anzianita.mesi()).isEqualTo(2);
        assertThat(anzianita.dal()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(anzianita.al()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(anzianita.inCorso()).isFalse();
        assertThat(anzianita.fonteInizio()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
        assertThat(anzianita.fonteFine()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
    }

    @Test
    void daGennaio2024ADicembre2025SonoDueAnniEsatti() {
        var anzianita = CalcoloAnzianita.calcola(
                contesto(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31), null), cedolini(ordinario(2024, 1)));

        assertThat(anzianita.anni()).isEqualTo(2);
        assertThat(anzianita.mesi()).isZero();
    }

    @Test
    void senzaDateUsaPrimoEUltimoCedolinoOrdinario() {
        var anzianita = CalcoloAnzianita.calcola(contesto(null, null, null),
                cedolini(ordinario(2024, 1), ordinario(2026, 2), ordinario(2025, 6)));

        assertThat(anzianita.dal()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(anzianita.al()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(anzianita.anni()).isEqualTo(2);
        assertThat(anzianita.mesi()).isEqualTo(2);
        assertThat(anzianita.inCorso()).isTrue();
        assertThat(anzianita.fonteInizio()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(anzianita.fonteFine()).isEqualTo(FonteDato.CEDOLINI);
    }

    @Test
    void tredicesimeEFineRapportoNonContanoComeMesi() {
        var anzianita = CalcoloAnzianita.calcola(contesto(null, null, null),
                cedolini(tredicesima(2023), ordinario(2024, 1), ordinario(2024, 12), tredicesima(2024),
                        fineRapporto(1, 2025, 3)));

        assertThat(anzianita.dal()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(anzianita.al()).isEqualTo(YearMonth.of(2024, 12));
        assertThat(anzianita.anni()).isEqualTo(1);
        assertThat(anzianita.mesi()).isZero();
    }

    @Test
    void assunzioneNotaEFineDaUltimoCedolino() {
        var anzianita = CalcoloAnzianita.calcola(contesto(LocalDate.of(2024, 6, 16), null, null),
                cedolini(ordinario(2024, 7), ordinario(2025, 2)));

        assertThat(anzianita.dal()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(anzianita.al()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(anzianita.anni()).isZero();
        assertThat(anzianita.mesi()).isEqualTo(9);
        assertThat(anzianita.fonteInizio()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
        assertThat(anzianita.fonteFine()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(anzianita.inCorso()).isTrue();
    }

    @Test
    void cessazioneNotaPrevaleSullUltimoCedolino() {
        var anzianita = CalcoloAnzianita.calcola(contesto(null, LocalDate.of(2024, 6, 15), null),
                cedolini(ordinario(2023, 1), ordinario(2024, 6), fineRapporto(1, 2024, 7)));

        assertThat(anzianita.dal()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(anzianita.al()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(anzianita.anni()).isEqualTo(1);
        assertThat(anzianita.mesi()).isEqualTo(6);
        assertThat(anzianita.fonteInizio()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(anzianita.fonteFine()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
        assertThat(anzianita.inCorso()).isFalse();
    }

    @Test
    void unSoloMeseValeUnMese() {
        var anzianita = CalcoloAnzianita.calcola(contesto(null, null, null), cedolini(ordinario(2024, 5)));

        assertThat(anzianita.anni()).isZero();
        assertThat(anzianita.mesi()).isEqualTo(1);
    }

    @Test
    void senzaCedoliniOrdinariNeDateNonDeterminabile() {
        var anzianita = CalcoloAnzianita.calcola(contesto(null, null, null),
                CedoliniOrdinati.di(List.<CedolinoNormalizzato>of(fineRapporto(1, 2024, 7).build())));

        assertThat(anzianita.dal()).isNull();
        assertThat(anzianita.al()).isNull();
        assertThat(anzianita.anni()).isZero();
        assertThat(anzianita.mesi()).isZero();
        assertThat(anzianita.fonteInizio()).isEqualTo(FonteDato.NON_DISPONIBILE);
        assertThat(anzianita.fonteFine()).isEqualTo(FonteDato.NON_DISPONIBILE);
    }

    @Test
    void dateIncoerentiDannoAnzianitaZero() {
        var anzianita = CalcoloAnzianita.calcola(
                contesto(LocalDate.of(2025, 3, 1), LocalDate.of(2024, 1, 31), null), cedolini(ordinario(2024, 1)));

        assertThat(anzianita.anni()).isZero();
        assertThat(anzianita.mesi()).isZero();
    }
}
