package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.domain.EstremiCedolini.Estremi;
import com.fcusumano.sam.cedolini.domain.EstremiCedolini.PeriodoCedolino;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstremiCedoliniTest {

    @Test
    void siConsideranoLeMensilitaOrdinarie() {
        var estremi = EstremiCedolini.di(List.of(
                new PeriodoCedolino(TipoMensilita.FINE_RAPPORTO, YearMonth.of(2024, 7)),
                new PeriodoCedolino(TipoMensilita.ORDINARIA, YearMonth.of(2024, 6)),
                new PeriodoCedolino(TipoMensilita.TREDICESIMA, YearMonth.of(2023, 12)),
                new PeriodoCedolino(TipoMensilita.ORDINARIA, YearMonth.of(2024, 1))));

        assertThat(estremi).isEqualTo(new Estremi(YearMonth.of(2024, 1), YearMonth.of(2024, 6)));
    }

    @Test
    void senzaOrdinarieSiUsanoTuttiICedolini() {
        var estremi = EstremiCedolini.di(List.of(
                new PeriodoCedolino(TipoMensilita.FINE_RAPPORTO, YearMonth.of(2024, 9)),
                new PeriodoCedolino(TipoMensilita.FINE_RAPPORTO, YearMonth.of(2024, 7))));

        assertThat(estremi).isEqualTo(new Estremi(YearMonth.of(2024, 7), YearMonth.of(2024, 9)));
    }

    @Test
    void senzaCedoliniNessunEstremo() {
        assertThat(EstremiCedolini.di(List.of())).isEqualTo(Estremi.NESSUNO);
    }
}
