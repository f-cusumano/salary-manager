package com.fcusumano.sam.cedolini.domain;

import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Progressivi dei cedolini di fine rapporto di un rapporto: TFR01, TFR02, ... in ordine cronologico del periodo di
 * retribuzione stampato. Quando arriva (o viene eliminato) un cedolino, i progressivi si ricalcolano sull'insieme
 * completo: un cedolino precedente a quelli presenti li fa slittare in avanti.
 */
public final class NumerazioneFineRapporto {

    private NumerazioneFineRapporto() {
    }

    /**
     * @param periodi periodi di retribuzione dei cedolini di fine rapporto del rapporto (distinti: l'unicita' e' sul
     *                periodo)
     * @return progressivo (da 1) per ciascun periodo, in ordine cronologico
     * @throws IllegalArgumentException se i periodi contengono null o duplicati
     */
    public static Map<YearMonth, Integer> numera(Collection<YearMonth> periodi) {
        var ordinati = periodi.stream().map(p -> Objects.requireNonNull(p, "periodo")).sorted().toList();
        var progressivi = new LinkedHashMap<YearMonth, Integer>();
        for (var periodo : ordinati) {
            if (progressivi.putIfAbsent(periodo, progressivi.size() + 1) != null) {
                throw new IllegalArgumentException("Periodo di fine rapporto ripetuto: " + periodo);
            }
        }
        return progressivi;
    }
}
