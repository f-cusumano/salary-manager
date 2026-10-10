package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.shared.TipoMensilita;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Estremi dei cedolini di un rapporto (primo e ultimo periodo di retribuzione), usati dall'anagrafica per il periodo
 * del rapporto quando mancano le date contrattuali. Si considerano le mensilita' ordinarie; se il rapporto ha solo
 * tredicesime, quattordicesime o cedolini di fine rapporto si usano tutti i cedolini.
 */
public final class EstremiCedolini {

    private EstremiCedolini() {
    }

    /** Periodo di retribuzione di un cedolino con il suo tipo. */
    public record PeriodoCedolino(TipoMensilita tipo, YearMonth periodo) {
    }

    /** Primo e ultimo periodo; entrambi null se non ci sono cedolini. */
    public record Estremi(YearMonth primo, YearMonth ultimo) {

        public static final Estremi NESSUNO = new Estremi(null, null);
    }

    public static Estremi di(Collection<PeriodoCedolino> cedolini) {
        var ordinari = cedolini.stream().filter(c -> c.tipo() == TipoMensilita.ORDINARIA).toList();
        var considerati = ordinari.isEmpty() ? List.copyOf(cedolini) : ordinari;
        if (considerati.isEmpty()) {
            return Estremi.NESSUNO;
        }
        var periodi = considerati.stream().map(PeriodoCedolino::periodo).toList();
        return new Estremi(periodi.stream().min(Comparator.naturalOrder()).orElseThrow(),
                periodi.stream().max(Comparator.naturalOrder()).orElseThrow());
    }
}
