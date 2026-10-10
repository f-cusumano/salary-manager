package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Buoni pasto: {@code presenti} e' true se sul cedolino compare una voce buoni pasto.
 */
public record BuoniPasto(boolean presenti, BigDecimal numero, BigDecimal valoreUnitario, BigDecimal importo) {

    public static final BuoniPasto ASSENTI = new BuoniPasto(false, null, null, null);
}
