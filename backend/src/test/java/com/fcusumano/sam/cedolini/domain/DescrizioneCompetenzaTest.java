package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.shared.Competenza;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DescrizioneCompetenzaTest {

    @Test
    void descrizioniLeggibili() {
        assertThat(DescrizioneCompetenza.di(new Competenza.Mensilita(2024, 1))).isEqualTo("Gennaio 2024");
        assertThat(DescrizioneCompetenza.di(Competenza.Mensilita.tredicesima(2023))).isEqualTo("Tredicesima 2023");
        assertThat(DescrizioneCompetenza.di(Competenza.Mensilita.quattordicesima(2024))).isEqualTo("Quattordicesima 2024");
        assertThat(DescrizioneCompetenza.di(new Competenza.FineRapporto(2, YearMonth.of(2024, 7))))
                .isEqualTo("Fine rapporto di Luglio 2024");
    }
}
