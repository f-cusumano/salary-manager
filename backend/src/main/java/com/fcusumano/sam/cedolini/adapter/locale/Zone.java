package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Area;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Etichetta;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Pagina;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Parola;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Funzioni di lettura per zone comuni ai layout: etichette (pattern sul testo compatto), valori sotto o accanto a
 * un'etichetta, righe di valori sotto una riga di intestazioni.
 */
final class Zone {

    /**
     * Distanza verticale massima tra testi della stessa linea visiva: etichette e valori stampati con caratteri
     * diversi possono avere linee di base distanti un paio di punti.
     */
    static final float STESSA_LINEA = 2.5f;
    /** Distanza verticale minima di un valore stampato sotto la sua etichetta. */
    static final float SOTTO_MINIMO = 3f;

    /** Filtro delle parole che sono importi o numeri interi. */
    static final Predicate<String> NUMERO = t -> Valori.importo(t) || Valori.INTERO.matcher(t).matches();
    /** Filtro delle parole che sono importi (con la virgola decimale). */
    static final Predicate<String> IMPORTO = Valori::importo;

    private Zone() {
    }

    /**
     * Pattern di un'etichetta stampata: parole senza spazi, in maiuscolo, apostrofi facoltativi.
     *
     * @param separatore regex tra una parola e l'altra ("" per le etichette normali, "S?" per le stampe Zucchetti
     *                   recenti che sostituiscono gli spazi con una "s")
     */
    static Pattern etichetta(String testo, String separatore) {
        var parole = testo.strip().toUpperCase(Locale.ROOT).split("\\s+");
        var regex = new StringBuilder();
        for (int i = 0; i < parole.length; i++) {
            if (i > 0) {
                regex.append(separatore);
            }
            regex.append(quota(parole[i]));
        }
        return Pattern.compile(regex.toString());
    }

    static Pattern etichetta(String testo) {
        return etichetta(testo, "");
    }

    private static String quota(String parola) {
        var r = new StringBuilder();
        for (char ch : parola.toCharArray()) {
            if (ch == '\'' || ch == '’') {
                r.append("['’`]?");
            } else if (Character.isLetterOrDigit(ch)) {
                r.append(ch);
            } else {
                r.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return r.toString();
    }

    // -------------------------------------------------------------------------------------------- etichette

    /** Prima etichetta con la linea di base tra y0 e y1. */
    static Optional<Etichetta> prima(Pagina p, Pattern etichetta, float y0, float y1) {
        return p.cerca(etichetta).stream().filter(e -> e.y() >= y0 && e.y() <= y1).findFirst();
    }

    /** Etichetta sulla stessa riga di un'altra (stessa linea di base, entro la tolleranza indicata), a destra di x0. */
    static Optional<Etichetta> sullaRiga(Pagina p, Pattern etichetta, float y, float tolleranza, float x0) {
        return p.cerca(etichetta).stream()
                .filter(e -> Math.abs(e.y() - y) <= tolleranza && e.x0() >= x0)
                .findFirst();
    }

    /** Etichette di una riga trovate con i pattern indicati (quelle assenti non compaiono nella mappa). */
    static <K> Map<K, Etichetta> etichette(Pagina p, float y, float tolleranza, Map<K, Pattern> pattern) {
        var trovate = new LinkedHashMap<K, Etichetta>();
        pattern.forEach((k, pt) -> sullaRiga(p, pt, y, tolleranza, -Float.MAX_VALUE).ifPresent(e -> trovate.put(k, e)));
        return trovate;
    }

    /**
     * Gruppi di parole di una riga separati da distacchi maggiori di {@code distacco}: le intestazioni stampate in
     * colonna ("MINIMO", "SCATTI ANZ.", "SUPERMINIMO").
     */
    static List<Gruppo> gruppi(Pagina p, float y, float x0, float x1, float distacco) {
        var parole = p.parole(new Area(x0, x1, y - DocumentoTesto.TOLLERANZA_RIGA, y + DocumentoTesto.TOLLERANZA_RIGA));
        var gruppi = new ArrayList<Gruppo>();
        var corrente = new ArrayList<Parola>();
        for (var w : parole.stream().sorted(Comparator.comparingDouble(Parola::x0)).toList()) {
            if (!corrente.isEmpty() && w.x0() - corrente.getLast().x1() > distacco) {
                gruppi.add(Gruppo.di(p, corrente));
                corrente = new ArrayList<>();
            }
            corrente.add(w);
        }
        if (!corrente.isEmpty()) {
            gruppi.add(Gruppo.di(p, corrente));
        }
        return gruppi;
    }

    /** Gruppo di parole adiacenti: testo e posizione come etichetta. */
    record Gruppo(String testo, Etichetta etichetta) {

        static Gruppo di(Pagina p, List<Parola> parole) {
            var testo = String.join(" ", parole.stream().map(Parola::testo).toList());
            return new Gruppo(testo, new Etichetta(p, parole.getFirst().x0(), parole.getLast().x1(),
                    parole.getFirst().y()));
        }

        String compatto() {
            return testo.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------------------------------------ valori

    /** Parole sotto l'etichetta, entro dyMax punti, con il centro tra x0 e x1. */
    static List<Parola> sotto(Pagina p, Etichetta e, float dyMax, float x0, float x1) {
        return p.parole(new Area(x0, x1, e.y() + SOTTO_MINIMO, e.y() + dyMax));
    }

    /** Parole della prima riga sotto l'etichetta che ha almeno una parola nell'area. */
    static List<Parola> primaRigaSotto(Pagina p, Etichetta e, float dyMax, float x0, float x1) {
        var parole = sotto(p, e, dyMax, x0, x1);
        if (parole.isEmpty()) {
            return List.of();
        }
        float y = parole.getFirst().y();
        return parole.stream().filter(w -> Math.abs(w.y() - y) <= DocumentoTesto.TOLLERANZA_RIGA).toList();
    }

    /** Linea di base della prima riga sotto l'etichetta con almeno una parola nell'area. */
    static Optional<Float> rigaSotto(Pagina p, Etichetta e, float dyMax, float x0, float x1) {
        return sotto(p, e, dyMax, x0, x1).stream().findFirst().map(Parola::y);
    }

    /** Testo dei caratteri di una riga tra x0 e x1 (campi stampati attaccati, separati per colonna); null se vuoto. */
    static String testoRiga(Pagina p, float y, float x0, float x1) {
        var testo = p.testo(new Area(x0, x1, y - DocumentoTesto.TOLLERANZA_RIGA, y + DocumentoTesto.TOLLERANZA_RIGA));
        return testo.isBlank() ? null : testo.strip();
    }

    /** Prima parola sotto l'etichetta che soddisfa il filtro. */
    static Optional<Parola> primaSotto(Pagina p, Etichetta e, float dyMax, float x0, float x1,
                                       Predicate<String> filtro) {
        return sotto(p, e, dyMax, x0, x1).stream().filter(w -> filtro.test(w.testo())).findFirst();
    }

    /** Parole sulla stessa riga dell'etichetta, con il centro tra x0 e x1. */
    static List<Parola> stessaRiga(Pagina p, Etichetta e, float x0, float x1) {
        return p.parole(new Area(x0, x1, e.y() - STESSA_LINEA, e.y() + STESSA_LINEA));
    }

    /** Prima parola a destra dell'etichetta sulla stessa riga (entro {@code distanza} punti) che soddisfa il filtro. */
    static Optional<Parola> aDestra(Pagina p, Etichetta e, float distanza, Predicate<String> filtro) {
        return stessaRiga(p, e, e.x1(), e.x1() + distanza).stream()
                .filter(w -> w.x0() >= e.x1() - 0.5f && filtro.test(w.testo()))
                .findFirst();
    }

    static String testo(List<Parola> parole) {
        return String.join(" ", parole.stream().map(Parola::testo).toList()).strip();
    }

    /**
     * Assegna ogni valore alla colonna che inizia piu' a destra senza superare l'inizio del valore (piu' una
     * tolleranza): e' la regola dei riquadri con i valori allineati a destra dentro la casella dell'etichetta. Se piu'
     * valori cadono nella stessa colonna resta il primo.
     */
    static <K> Map<K, Parola> perInizio(Map<K, Etichetta> colonne, List<Parola> valori, float tolleranza) {
        var ordinate = colonne.entrySet().stream()
                .sorted(Comparator.comparingDouble(c -> c.getValue().x0()))
                .toList();
        var risultato = new LinkedHashMap<K, Parola>();
        for (var v : valori) {
            K scelta = null;
            for (var c : ordinate) {
                if (c.getValue().x0() <= v.x0() + tolleranza) {
                    scelta = c.getKey();
                }
            }
            if (scelta != null) {
                risultato.putIfAbsent(scelta, v);
            }
        }
        return risultato;
    }

    /**
     * Valori che soddisfano il filtro nella fascia sotto una riga di etichette (fino a dyMax punti dalla piu' bassa),
     * tra la prima etichetta e x1, assegnati per inizio colonna.
     */
    static <K> Map<K, Parola> valoriSotto(Pagina p, Map<K, Etichetta> colonne, float dyMax, float x1,
                                          Predicate<String> filtro) {
        if (colonne.isEmpty()) {
            return Map.of();
        }
        float y = (float) colonne.values().stream().mapToDouble(Etichetta::y).max().orElseThrow();
        float x0 = (float) colonne.values().stream().mapToDouble(Etichetta::x0).min().orElseThrow();
        var valori = p.parole(new Area(x0 - 4, x1, y + SOTTO_MINIMO, y + dyMax), filtro);
        return perInizio(colonne, valori, 4);
    }

    static <K> BigDecimal importo(Map<K, Parola> valori, K chiave) {
        var w = valori.get(chiave);
        return w == null ? null : Valori.leggiNumero(w.testo()).orElse(null);
    }

    static BigDecimal importo(Optional<Parola> parola) {
        return parola.flatMap(w -> Valori.leggiNumero(w.testo())).orElse(null);
    }

    static BigDecimal piu(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a.add(b);
    }

    static BigDecimal oZero(BigDecimal valore) {
        return valore == null ? BigDecimal.ZERO : valore;
    }
}
