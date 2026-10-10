package com.fcusumano.sam.calcoli.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ParametriCalcoloTest {

    private static ParametriCalcolo parametri(String divisore, String aliquota, String tolleranza, int mensilita,
                                              int buoniPasto) {
        return new ParametriCalcolo(new BigDecimal(divisore), new BigDecimal("173"), new BigDecimal("8"),
                new BigDecimal("13.5"), new BigDecimal(aliquota), new BigDecimal("0.17"), new BigDecimal(tolleranza),
                mensilita, buoniPasto, new BigDecimal("0.03"), 12);
    }

    @Test
    void rifiutaParametriNonValidi() {
        assertThatThrownBy(() -> parametri("0", "0.23", "0.01", 13, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametri("26", "1.5", "0.01", 13, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametri("26", "0.23", "-1", 13, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametri("26", "0.23", "0.01", 15, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametri("26", "0.23", "0.01", 13, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametriCalcolo(new BigDecimal("26"), new BigDecimal("173"), new BigDecimal("8"),
                new BigDecimal("13.5"), new BigDecimal("0.23"), new BigDecimal("0.17"), new BigDecimal("0.01"), 13, 3,
                new BigDecimal("1.2"), 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametriCalcolo(new BigDecimal("26"), new BigDecimal("173"), new BigDecimal("8"),
                new BigDecimal("13.5"), new BigDecimal("0.23"), new BigDecimal("0.17"), new BigDecimal("0.01"), 13, 3,
                new BigDecimal("0.03"), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
