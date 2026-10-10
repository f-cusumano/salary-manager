package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

/**
 * Valuta tutte le regole registrate su un rapporto, da zero: elimina i duplicati (una segnalazione per anomalia:
 * stesso tipo, stesso cedolino, stessa competenza) e ordina il risultato per gravita' decrescente, poi in ordine
 * cronologico (competenza o, in mancanza, cedolino di riferimento; le segnalazioni sull'intero rapporto in coda), poi
 * per tipo e titolo. Il risultato e' deterministico: non dipende dall'ordine di registrazione delle regole.
 * <p>
 * Una regola che solleva un'eccezione non blocca le altre (il ricalcolo avviene nella transazione di acquisizione del
 * cedolino): l'errore e' passato al gestore indicato e la regola non produce segnalazioni.
 */
public final class MotoreSegnalazioni {

    private final List<RegolaSegnalazione> regole;
    private final BiConsumer<RegolaSegnalazione, RuntimeException> gestoreErrori;

    public MotoreSegnalazioni(List<RegolaSegnalazione> regole,
                              BiConsumer<RegolaSegnalazione, RuntimeException> gestoreErrori) {
        this.regole = regole.stream().sorted(Comparator.comparing(RegolaSegnalazione::codice)).toList();
        this.gestoreErrori = Objects.requireNonNull(gestoreErrori, "gestoreErrori");
    }

    /** Segnalazioni del rapporto, senza duplicati e ordinate. Nessuna segnalazione per un rapporto senza cedolini. */
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        if (contesto.cedolini().isEmpty()) {
            return List.of();
        }
        var tutte = new ArrayList<SegnalazioneRilevata>();
        for (var regola : regole) {
            try {
                tutte.addAll(regola.valuta(contesto));
            } catch (RuntimeException e) {
                gestoreErrori.accept(regola, e);
            }
        }
        tutte.sort(ordinamento(contesto));
        var uniche = new LinkedHashMap<Chiave, SegnalazioneRilevata>();
        tutte.forEach(s -> uniche.putIfAbsent(new Chiave(s.tipo(), s.cedolinoId(), s.competenza()), s));
        return List.copyOf(uniche.values());
    }

    /** Gravita' decrescente, poi data, tipo, titolo e descrizione (per un ordine totale e stabile). */
    static Comparator<SegnalazioneRilevata> ordinamento(ContestoRapporto contesto) {
        ToLongFunction<SegnalazioneRilevata> data = s -> {
            if (s.competenza() != null) {
                return s.competenza().chiaveOrdinamento();
            }
            if (s.cedolinoId() != null) {
                return contesto.cedolino(s.cedolinoId()).map(c -> c.competenza().chiaveOrdinamento())
                        .orElse(Long.MAX_VALUE);
            }
            return Long.MAX_VALUE;
        };
        return Comparator.comparing(SegnalazioneRilevata::gravita, Comparator.comparingInt(Gravita::ordinal).reversed())
                .thenComparingLong(data)
                .thenComparing(SegnalazioneRilevata::tipo)
                .thenComparing(SegnalazioneRilevata::titolo)
                .thenComparing(SegnalazioneRilevata::descrizione);
    }

    private record Chiave(TipoSegnalazione tipo, CedolinoId cedolinoId, Competenza.Mensilita competenza) {
    }
}
