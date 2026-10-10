package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Cedolini di un rapporto in ordine cronologico crescente di competenza (la tredicesima segue dicembre, la
 * quattordicesima segue luglio, i cedolini di fine rapporto seguono il mese del loro periodo; a parita' di competenza
 * decide l'id), con le ricerche usate dai calcoli.
 */
public record CedoliniOrdinati(List<CedolinoNormalizzato> tutti) {

    private static final Comparator<CedolinoNormalizzato> ORDINE =
            Comparator.comparing(CedolinoNormalizzato::competenza, Competenza.ORDINE_CRONOLOGICO)
                    .thenComparingLong(c -> c.id() == null ? 0L : c.id().valore());

    public CedoliniOrdinati {
        tutti = tutti == null ? List.of() : tutti.stream().sorted(ORDINE).toList();
    }

    public static CedoliniOrdinati di(List<CedolinoNormalizzato> cedolini) {
        return new CedoliniOrdinati(cedolini);
    }

    public boolean vuoto() {
        return tutti.isEmpty();
    }

    /** Mensilita' ordinarie (mesi 1..12) in ordine cronologico. */
    public List<CedolinoNormalizzato> ordinari() {
        return tutti.stream().filter(CedoliniOrdinati::ordinario).toList();
    }

    /** Ultimo cedolino (di qualunque tipo) che soddisfa la condizione. */
    public Optional<CedolinoNormalizzato> ultimo(Predicate<CedolinoNormalizzato> condizione) {
        return tutti.reversed().stream().filter(condizione).findFirst();
    }

    /** Ultima mensilita' ordinaria che soddisfa la condizione. */
    public Optional<CedolinoNormalizzato> ultimoOrdinario(Predicate<CedolinoNormalizzato> condizione) {
        return ultimo(c -> ordinario(c) && condizione.test(c));
    }

    public static boolean ordinario(CedolinoNormalizzato cedolino) {
        return cedolino.tipo() == TipoMensilita.ORDINARIA;
    }

    /**
     * Mese di un cedolino: per le mensilita' ordinarie il mese di competenza, per gli altri il periodo di retribuzione
     * stampato (per tredicesima/quattordicesima senza periodo: dicembre/luglio dell'anno di competenza).
     */
    public static YearMonth mese(CedolinoNormalizzato cedolino) {
        return switch (cedolino.competenza()) {
            case Competenza.Mensilita m when m.mese() <= 12 -> m.comeYearMonth();
            case Competenza.Mensilita m when cedolino.periodoRetribuzione() != null -> cedolino.periodoRetribuzione();
            case Competenza.Mensilita m -> YearMonth.of(m.anno(), m.mese() == 13 ? 12 : 7);
            case Competenza.FineRapporto f -> f.periodoRetribuzione();
        };
    }
}
