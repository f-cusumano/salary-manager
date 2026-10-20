package com.fcusumano.sam.ccnl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegolaPreavvisoTest {

    @ParameterizedTest(name = "\"{0}\" = {1}")
    @CsvSource(delimiter = ';', value = {
            "3;3",
            "3°;3",
            "3° Livello;3",
            "Liv. 4;4",
            "IV;4",
            "VI liv.;6",
            "3° Super;3S",
            "6S;6S",
            "Quadro;Q",
            "Quadro A;QA",
            "Quadri;Q",
            "c3;C3",
            "D1;D1",
            "1ª categoria;1"})
    void normalizzaIlLivelloStampato(String stampato, String atteso) {
        assertThat(RegolaPreavviso.normalizzaLivello(stampato)).isEqualTo(atteso);
    }

    @Test
    void livelloVuotoONullNonENormalizzabile() {
        assertThat(RegolaPreavviso.normalizzaLivello(null)).isNull();
        assertThat(RegolaPreavviso.normalizzaLivello(" ° ")).isNull();
        assertThat(RegolaPreavviso.normalizzaLivello("Livello")).isNull();
    }

    @Test
    void comprendeSoloILivelliDelGruppo() {
        var regola = new RegolaPreavviso(Set.of("2", "3"), "2° e 3° livello", 0, new DurataPreavviso(0, 20),
                new DurataPreavviso(0, 30), "Art. 247");

        assertThat(regola.comprende("3° livello")).isTrue();
        assertThat(regola.comprende("III")).isTrue();
        assertThat(regola.comprende("3S")).isFalse();
        assertThat(regola.comprende("4")).isFalse();
        assertThat(regola.comprende(null)).isFalse();
    }

    @Test
    void duratePositive() {
        assertThatThrownBy(() -> new DurataPreavviso(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurataPreavviso(-1, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
