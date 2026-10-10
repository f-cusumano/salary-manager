package com.fcusumano.sam.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Utilita' minime per gli importi. Gli importi sono sempre {@link BigDecimal} (a DB NUMERIC(15,5)), mai double.
 */
public final class Importi {

    /** Scala di memorizzazione (NUMERIC(15,5)). */
    public static final int SCALA = 5;
    /** Scala di presentazione in euro. */
    public static final int SCALA_EURO = 2;
    public static final RoundingMode ARROTONDAMENTO = RoundingMode.HALF_UP;

    private Importi() {
    }

    /** Porta l'importo alla scala di memorizzazione; null resta null. */
    public static BigDecimal normalizza(BigDecimal importo) {
        return importo == null ? null : importo.setScale(SCALA, ARROTONDAMENTO);
    }

    /** Arrotonda al centesimo; null resta null. */
    public static BigDecimal euro(BigDecimal importo) {
        return importo == null ? null : importo.setScale(SCALA_EURO, ARROTONDAMENTO);
    }

    public static BigDecimal zeroSeNull(BigDecimal importo) {
        return importo == null ? BigDecimal.ZERO : importo;
    }

    public static boolean nulloOZero(BigDecimal importo) {
        return importo == null || importo.signum() == 0;
    }

    /** Somma ignorando i null; restituisce null se tutti gli addendi sono null. */
    public static BigDecimal sommaNonNulli(BigDecimal... addendi) {
        return Arrays.stream(addendi).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }

    /** Confronto numerico che ignora la scala (1.0 == 1.00000). */
    public static boolean uguali(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
