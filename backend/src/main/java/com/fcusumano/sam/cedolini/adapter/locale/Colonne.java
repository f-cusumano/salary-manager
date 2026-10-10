package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Etichetta;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Parola;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assegnazione dei valori di una riga alle colonne di una riga di intestazione: ogni valore va alla colonna con il
 * centro orizzontale piu' vicino, entro una distanza massima. Se due valori finiscono nella stessa colonna vince il
 * piu' vicino e l'altro resta non assegnato (mai un valore in una colonna sbagliata per esclusione).
 *
 * @param <K> chiave della colonna
 */
final class Colonne<K> {

    private final Map<K, Float> centri = new LinkedHashMap<>();

    /** Aggiunge una colonna con il centro indicato. */
    Colonne<K> colonna(K chiave, float centro) {
        centri.put(chiave, centro);
        return this;
    }

    /** Aggiunge una colonna centrata sull'etichetta. */
    Colonne<K> colonna(K chiave, Etichetta etichetta) {
        return colonna(chiave, etichetta.centro());
    }

    boolean vuota() {
        return centri.isEmpty();
    }

    /** Colonna piu' vicina al centro indicato, entro la distanza massima; null se nessuna. */
    K vicina(float centro, float distanzaMassima) {
        K migliore = null;
        float distanza = Float.MAX_VALUE;
        for (var e : centri.entrySet()) {
            float d = Math.abs(e.getValue() - centro);
            if (d < distanza) {
                distanza = d;
                migliore = e.getKey();
            }
        }
        return distanza <= distanzaMassima ? migliore : null;
    }

    /** Valori assegnati alle colonne (una parola per colonna al massimo). */
    Map<K, Parola> assegna(List<Parola> valori, float distanzaMassima) {
        var migliori = new HashMap<K, Parola>();
        var distanze = new HashMap<K, Float>();
        for (var v : valori) {
            var k = vicina(v.centro(), distanzaMassima);
            if (k == null) {
                continue;
            }
            float d = Math.abs(centri.get(k) - v.centro());
            if (!distanze.containsKey(k) || d < distanze.get(k)) {
                migliori.put(k, v);
                distanze.put(k, d);
            }
        }
        var ordinati = new LinkedHashMap<K, Parola>();
        new ArrayList<>(centri.keySet()).forEach(k -> {
            if (migliori.containsKey(k)) {
                ordinati.put(k, migliori.get(k));
            }
        });
        return ordinati;
    }
}
