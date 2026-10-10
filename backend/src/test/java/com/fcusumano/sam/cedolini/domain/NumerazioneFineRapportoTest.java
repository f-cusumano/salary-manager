package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NumerazioneFineRapportoTest {

    @Test
    void progressiviInOrdineCronologicoDelPeriodo() {
        var progressivi = NumerazioneFineRapporto.numera(
                List.of(YearMonth.of(2024, 9), YearMonth.of(2024, 7), YearMonth.of(2025, 1)));

        assertThat(progressivi).containsExactly(
                Map.entry(YearMonth.of(2024, 7), 1),
                Map.entry(YearMonth.of(2024, 9), 2),
                Map.entry(YearMonth.of(2025, 1), 3));
    }

    @Test
    void nessunPeriodoNessunProgressivo() {
        assertThat(NumerazioneFineRapporto.numera(List.of())).isEmpty();
    }

    @Test
    void periodiRipetutiNonSonoAmmessi() {
        assertThatThrownBy(() -> NumerazioneFineRapporto.numera(List.of(YearMonth.of(2024, 7), YearMonth.of(2024, 7))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
