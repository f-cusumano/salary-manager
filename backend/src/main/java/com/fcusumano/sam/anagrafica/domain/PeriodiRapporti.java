package com.fcusumano.sam.anagrafica.domain;

import com.fcusumano.sam.anagrafica.FontePeriodo;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Periodo dei rapporti di una persona e ordinamento per la UI.
 * <ul>
 *   <li>{@code da}: mese di assunzione se stampato, altrimenti primo cedolino;</li>
 *   <li>un rapporto e' <b>attuale</b> se non ha data di cessazione ed e' quello con l'ultimo cedolino piu' recente
 *       tra i rapporti della persona (a pari merito sono attuali tutti);</li>
 *   <li>{@code a}: mese di cessazione se stampato; altrimenti ultimo cedolino per i rapporti non attuali; null per
 *       quelli attuali.</li>
 * </ul>
 */
public final class PeriodiRapporti {

    /**
     * Ordine cronologico inverso: attuali in alto, poi per inizio decrescente, poi per fine decrescente; a parita'
     * il rapporto registrato per ultimo. I periodi senza inizio vanno in fondo.
     */
    public static final Comparator<RapportoConPeriodo> ORDINE_CRONOLOGICO_INVERSO =
            Comparator.comparing((RapportoConPeriodo r) -> !r.periodo().attuale())
                    .thenComparing(r -> r.periodo().da(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(r -> r.periodo().a(), Comparator.nullsFirst(Comparator.reverseOrder()))
                    .thenComparing(r -> r.rapporto().id().valore(), Comparator.reverseOrder());

    /** Rapporto con il periodo calcolato. */
    public record RapportoConPeriodo(RapportoLavoro rapporto, PeriodoRapporto periodo) {
    }

    private PeriodiRapporti() {
    }

    /**
     * Calcola il periodo di ciascun rapporto della stessa persona.
     *
     * @param rapportiDellaPersona tutti i rapporti della persona (servono per stabilire quale e' attuale)
     * @return periodi per id, nello stesso ordine dei rapporti in ingresso
     */
    public static Map<RapportoLavoroId, PeriodoRapporto> calcola(List<RapportoLavoro> rapportiDellaPersona) {
        var ultimoPiuRecente = rapportiDellaPersona.stream()
                .map(RapportoLavoro::ultimoCedolino)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        var periodi = new LinkedHashMap<RapportoLavoroId, PeriodoRapporto>();
        for (var rapporto : rapportiDellaPersona) {
            periodi.put(rapporto.id(), periodo(rapporto, ultimoPiuRecente));
        }
        return periodi;
    }

    /** Rapporti con periodo, in ordine cronologico inverso. */
    public static List<RapportoConPeriodo> ordina(List<RapportoLavoro> rapportiDellaPersona) {
        var periodi = calcola(rapportiDellaPersona);
        return rapportiDellaPersona.stream()
                .map(r -> new RapportoConPeriodo(r, periodi.get(r.id())))
                .sorted(ORDINE_CRONOLOGICO_INVERSO)
                .toList();
    }

    private static PeriodoRapporto periodo(RapportoLavoro rapporto, YearMonth ultimoPiuRecente) {
        YearMonth da;
        FontePeriodo fonteInizio;
        if (rapporto.dataAssunzione() != null) {
            da = YearMonth.from(rapporto.dataAssunzione());
            fonteInizio = FontePeriodo.DATA_CONTRATTUALE;
        } else if (rapporto.primoCedolino() != null) {
            da = rapporto.primoCedolino();
            fonteInizio = FontePeriodo.CEDOLINI;
        } else {
            da = null;
            fonteInizio = FontePeriodo.NON_DISPONIBILE;
        }
        if (rapporto.dataCessazione() != null) {
            return new PeriodoRapporto(da, YearMonth.from(rapporto.dataCessazione()), false, fonteInizio,
                    FontePeriodo.DATA_CONTRATTUALE);
        }
        boolean attuale = Objects.equals(rapporto.ultimoCedolino(), ultimoPiuRecente);
        if (attuale) {
            return new PeriodoRapporto(da, null, true, fonteInizio, FontePeriodo.NON_DISPONIBILE);
        }
        return new PeriodoRapporto(da, rapporto.ultimoCedolino(), false, fonteInizio,
                rapporto.ultimoCedolino() != null ? FontePeriodo.CEDOLINI : FontePeriodo.NON_DISPONIBILE);
    }
}
