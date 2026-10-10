package com.fcusumano.sam.anagrafica.domain;

import java.time.YearMonth;

/**
 * Criterio di recenza dei dati letti dai cedolini: vince il cedolino con periodo di riferimento piu' recente.
 */
public final class Recenza {

    private Recenza() {
    }

    /**
     * True se i nuovi dati possono sovrascrivere quelli presenti: riferimento nuovo uguale o successivo a quello
     * presente. Un riferimento mancante da una delle due parti non impedisce l'aggiornamento.
     */
    public static boolean sovrascrive(YearMonth nuovo, YearMonth presente) {
        return nuovo == null || presente == null || !nuovo.isBefore(presente);
    }

    /** Il piu' recente dei due (null se entrambi null). */
    public static YearMonth piuRecente(YearMonth a, YearMonth b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
