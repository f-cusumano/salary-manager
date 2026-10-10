package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.DiFineRapporto;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.Esito;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.Mensile;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Classificazione completa dei cedolini di un rapporto rispetto alla sua data di cessazione: ogni cedolino riceve la
 * competenza che {@link DeterminazioneCompetenza} assegna al suo tipo dichiarato e al suo periodo stampato, cosi' lo
 * stato dipende solo dai cedolini presenti e dalla cessazione e non dall'ordine di caricamento o di eliminazione.
 * <ul>
 *   <li>le mensilita' (ordinarie, tredicesime, quattordicesime) con periodo successivo al mese di cessazione diventano
 *       cedolini di fine rapporto, salvo riassunzione stampata;</li>
 *   <li>i cedolini di fine rapporto che l'estrazione aveva dichiarato mensilita' tornano mensilita' quando la
 *       cessazione sparisce o si sposta;</li>
 *   <li>un cedolino cambia classificazione solo se la nuova chiave di unicita' (mese 01..14 oppure periodo di fine
 *       rapporto) e' libera; altrimenti resta com'e' (conflitto da risolvere a mano, mai una sovrascrittura);</li>
 *   <li>i progressivi TFR01, TFR02, ... sono ricalcolati in ordine cronologico del periodo stampato.</li>
 * </ul>
 * Funzione pura e deterministica.
 */
public final class ClassificazioneRapporto {

    /** Ordine in cui si provano i cambi di classificazione: periodo stampato, poi id. */
    private static final Comparator<Cedolino> ORDINE = Comparator.comparing(Cedolino::periodo)
            .thenComparingLong(Cedolino::id);

    private ClassificazioneRapporto() {
    }

    /**
     * Cedolino del rapporto da classificare.
     *
     * @param id                 identificativo (anche provvisorio, per un cedolino non ancora salvato): univoco
     * @param dichiarato         tipo dichiarato dall'estrazione (mai {@link TipoDichiarato#AGGIUNTIVA})
     * @param periodo            periodo di retribuzione stampato
     * @param assunzioneStampata data di assunzione stampata sul cedolino, null se assente
     * @param attuale            competenza attuale (per un cedolino non ancora salvato: quella gia' determinata)
     * @param fisso              true se la classificazione attuale non va cambiata (cedolino in acquisizione, la cui
     *                           chiave e' gia' stata verificata libera)
     */
    public record Cedolino(long id, TipoDichiarato dichiarato, YearMonth periodo, LocalDate assunzioneStampata,
                           Competenza attuale, boolean fisso) {

        public Cedolino {
            Objects.requireNonNull(dichiarato, "dichiarato");
            Objects.requireNonNull(periodo, "periodo");
            Objects.requireNonNull(attuale, "attuale");
            if (dichiarato == TipoDichiarato.AGGIUNTIVA) {
                throw new IllegalArgumentException("Un cedolino aggiuntivo non viene archiviato");
            }
        }
    }

    /** Nuova competenza di un cedolino. */
    public record Assegnazione(long id, Competenza competenza) {
    }

    /**
     * Calcola le competenze da cambiare.
     *
     * @param cedolini   tutti i cedolini del rapporto (con chiavi di unicita' attuali distinte)
     * @param cessazione data di cessazione del rapporto, null se in corso o non nota
     * @return assegnazioni dei soli cedolini la cui competenza cambia, nell'ordine in cui applicarle: prima i cambi di
     *         classificazione (ciascuno verso una chiave gia' libera al momento dell'applicazione), poi le sole
     *         rinumerazioni dei progressivi TFR
     */
    public static List<Assegnazione> classifica(Collection<Cedolino> cedolini, LocalDate cessazione) {
        var occupate = new HashMap<Esito, Long>();
        var classificazione = new HashMap<Long, Esito>();
        for (var c : cedolini) {
            var chiave = chiave(c.attuale());
            if (occupate.putIfAbsent(chiave, c.id()) != null || classificazione.put(c.id(), chiave) != null) {
                throw new IllegalArgumentException("Cedolini con la stessa chiave o lo stesso id: " + c.id());
            }
        }

        var cambi = new ArrayList<Long>();
        var inAttesa = new ArrayList<>(cedolini.stream()
                .filter(c -> !c.fisso())
                .filter(c -> !obiettivo(c, cessazione).equals(classificazione.get(c.id())))
                .sorted(ORDINE)
                .toList());
        boolean spostato = true;
        while (spostato) {
            spostato = false;
            for (var iteratore = inAttesa.iterator(); iteratore.hasNext(); ) {
                var c = iteratore.next();
                var obiettivo = obiettivo(c, cessazione);
                if (!occupate.containsKey(obiettivo)) {
                    occupate.remove(classificazione.get(c.id()));
                    occupate.put(obiettivo, c.id());
                    classificazione.put(c.id(), obiettivo);
                    cambi.add(c.id());
                    iteratore.remove();
                    spostato = true;
                }
            }
        }
        return assegnazioni(cedolini, classificazione, cambi);
    }

    private static List<Assegnazione> assegnazioni(Collection<Cedolino> cedolini, Map<Long, Esito> classificazione,
                                                   List<Long> cambi) {
        var progressivi = NumerazioneFineRapporto.numera(classificazione.values().stream()
                .filter(DiFineRapporto.class::isInstance)
                .map(e -> ((DiFineRapporto) e).periodo())
                .toList());
        var finali = new LinkedHashMap<Long, Competenza>();
        for (var id : cambi) {
            finali.put(id, competenza(classificazione.get(id), progressivi));
        }
        cedolini.stream().sorted(ORDINE).forEach(c -> {
            var finale = competenza(classificazione.get(c.id()), progressivi);
            if (!finali.containsKey(c.id()) && !finale.equals(c.attuale())) {
                finali.put(c.id(), finale);
            }
        });
        return finali.entrySet().stream().map(e -> new Assegnazione(e.getKey(), e.getValue())).toList();
    }

    private static Esito obiettivo(Cedolino c, LocalDate cessazione) {
        return DeterminazioneCompetenza.determina(c.dichiarato(), c.periodo(), c.assunzioneStampata(), cessazione);
    }

    /** Chiave di unicita' di una competenza: il mese per le mensilita', il periodo stampato per il fine rapporto. */
    private static Esito chiave(Competenza competenza) {
        return switch (competenza) {
            case Competenza.Mensilita m -> new Mensile(m);
            case Competenza.FineRapporto f -> new DiFineRapporto(f.periodoRetribuzione());
        };
    }

    private static Competenza competenza(Esito esito, Map<YearMonth, Integer> progressivi) {
        return switch (esito) {
            case Mensile m -> m.competenza();
            case DiFineRapporto f -> new Competenza.FineRapporto(progressivi.get(f.periodo()), f.periodo());
        };
    }
}
