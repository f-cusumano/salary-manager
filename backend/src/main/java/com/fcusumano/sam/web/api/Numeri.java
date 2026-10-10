package com.fcusumano.sam.web.api;

import com.fcusumano.sam.shared.Importi;
import java.math.BigDecimal;

/**
 * Presentazione dei numeri decimali nelle risposte JSON.
 */
final class Numeri {

    private Numeri() {
    }

    /** Importo in euro arrotondato al centesimo; null resta null. */
    static BigDecimal euro(BigDecimal importo) {
        return Importi.euro(importo);
    }

    /**
     * Quantita', tariffe, divisori e percentuali senza zeri finali superflui (8.00000 -> 8, 7.50000 -> 7.5) e senza
     * notazione esponenziale; null resta null.
     */
    static BigDecimal esatto(BigDecimal valore) {
        if (valore == null) {
            return null;
        }
        var ridotto = valore.stripTrailingZeros();
        return ridotto.scale() < 0 ? ridotto.setScale(0) : ridotto;
    }
}
