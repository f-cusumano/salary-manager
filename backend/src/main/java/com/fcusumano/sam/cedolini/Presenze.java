package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Riepilogo presenze stampato (le singole assenze sono voci di categoria PRESENZA).
 */
public record Presenze(BigDecimal giorniLavorati, BigDecimal oreLavorate, BigDecimal giorniRetribuiti) {

    public static final Presenze VUOTE = new Presenze(null, null, null);
}
