package com.fcusumano.sam.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetenzaTest {

    @Test
    void codiciDiCompetenza() {
        assertThat(new Competenza.Mensilita(2024, 1).codice()).isEqualTo("01");
        assertThat(Competenza.Mensilita.tredicesima(2024).codice()).isEqualTo("13");
        assertThat(Competenza.Mensilita.quattordicesima(2024).tipo()).isEqualTo(TipoMensilita.QUATTORDICESIMA);
        assertThat(new Competenza.FineRapporto(1, YearMonth.of(2020, 4)).codice()).isEqualTo("TFR01");
    }

    @Test
    void meseFuoriIntervalloRifiutato() {
        assertThatThrownBy(() -> new Competenza.Mensilita(2024, 15)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordineCronologico() {
        var dicembre = new Competenza.Mensilita(2023, 12);
        var tredicesima = Competenza.Mensilita.tredicesima(2023);
        var gennaio = new Competenza.Mensilita(2024, 1);
        var tfr = new Competenza.FineRapporto(1, YearMonth.of(2024, 1));
        var lista = new ArrayList<>(List.<Competenza>of(tfr, gennaio, tredicesima, dicembre));
        lista.sort(Competenza.ORDINE_CRONOLOGICO);
        assertThat(lista).containsExactly(dicembre, tredicesima, gennaio, tfr);
    }

    @Test
    void mesiInclusi() {
        assertThat(Periodi.mesiInclusi(YearMonth.of(2024, 1), YearMonth.of(2026, 2))).isEqualTo(26);
        assertThat(Periodi.descrizione(YearMonth.of(2024, 1))).isEqualTo("Gennaio 2024");
    }
}
