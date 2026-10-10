package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Buoni pasto: SI" se almeno uno degli ultimi N cedolini ordinari (N configurato, 3 per default) riporta buoni pasto:
 * il dato estratto {@code buoniPasto.presenti} oppure una voce con descrizione da buono pasto ("buoni pasto",
 * "buono pasto", "ticket restaurant", "ticket pasto"). Si guardano piu' mesi perche' un mese di ferie o malattia puo'
 * non avere buoni.
 */
public final class CalcoloBuoniPasto {

    private static final Pattern VOCE_BUONI_PASTO =
            Pattern.compile("buon[oi]\\s*pasto|ticket\\s*(restaurant|pasto)");

    private CalcoloBuoniPasto() {
    }

    public static boolean calcola(CedoliniOrdinati cedolini, ParametriCalcolo parametri) {
        var ordinari = cedolini.ordinari();
        var ultimi = ordinari.subList(Math.max(0, ordinari.size() - parametri.cedoliniBuoniPasto()), ordinari.size());
        return ultimi.stream().anyMatch(CalcoloBuoniPasto::conBuoniPasto);
    }

    static boolean conBuoniPasto(CedolinoNormalizzato cedolino) {
        return cedolino.buoniPasto().presenti() || cedolino.voci().stream()
                .anyMatch(v -> v.descrizione() != null
                        && VOCE_BUONI_PASTO.matcher(v.descrizione().toLowerCase(Locale.ROOT)).find());
    }
}
