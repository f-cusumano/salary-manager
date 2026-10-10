package com.fcusumano.sam.cedolini.adapter.locale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Testo di un PDF con le posizioni: pagine, righe, parole e caratteri. Coordinate in punti tipografici con l'origine
 * in alto a sinistra della pagina (x verso destra, y verso il basso), come le restituisce PDFBox ({@code XDirAdj},
 * {@code YDirAdj}); la y di un carattere e' quella della sua linea di base.
 * <p>
 * I riconoscitori dei layout lavorano per aree: cercano le etichette stampate ({@link #cerca}) e leggono i valori
 * nelle zone vicine ({@link Pagina#parole(Area)}, {@link Pagina#testo(Area)}). Le etichette si confrontano senza spazi
 * e in maiuscolo, cosi' si ritrovano anche le scritte spaziate ("T O T A L E") o con i caratteri separati da
 * crenature. Nessun metodo logga il contenuto.
 */
final class DocumentoTesto {

    /** Distanza verticale massima tra caratteri della stessa riga. */
    static final float TOLLERANZA_RIGA = 1.0f;

    private final List<Pagina> pagine;

    DocumentoTesto(List<Pagina> pagine) {
        this.pagine = List.copyOf(pagine);
    }

    List<Pagina> pagine() {
        return pagine;
    }

    Pagina ultimaPagina() {
        return pagine.getLast();
    }

    /** Numero di caratteri alfanumerici del documento: zero (o quasi) per le scansioni senza testo. */
    int caratteriSignificativi() {
        return pagine.stream().mapToInt(Pagina::caratteriSignificativi).sum();
    }

    /** Tutto il testo senza spazi e in maiuscolo, per i controlli di riconoscimento del layout. */
    String compatto() {
        var testo = new StringBuilder();
        pagine.forEach(p -> p.righe().forEach(r -> testo.append(r.compatta()).append('\n')));
        return testo.toString();
    }

    /** Etichette trovate in tutto il documento, nell'ordine delle pagine e delle righe. */
    List<Etichetta> cerca(Pattern etichetta) {
        var trovate = new ArrayList<Etichetta>();
        pagine.forEach(p -> trovate.addAll(p.cerca(etichetta)));
        return trovate;
    }

    // ------------------------------------------------------------------------------------------------------- modello

    /** Carattere (o legatura) stampato: testo, ascissa iniziale, larghezza, linea di base e altezza. */
    record Carattere(String testo, float x, float larghezza, float y, float altezza) {

        float x1() {
            return x + larghezza;
        }

        float centro() {
            return x + larghezza / 2;
        }

        boolean spazio() {
            return testo.isBlank();
        }
    }

    /** Parola: caratteri consecutivi senza spazi ne' distacchi orizzontali. */
    record Parola(String testo, float x0, float x1, float y) {

        float centro() {
            return (x0 + x1) / 2;
        }
    }

    /** Etichetta trovata: estremi orizzontali, linea di base e pagina. */
    record Etichetta(Pagina pagina, float x0, float x1, float y) {

        float centro() {
            return (x0 + x1) / 2;
        }
    }

    /**
     * Area rettangolare della pagina (estremi inclusi). Una parola o un carattere vi appartiene se il suo centro
     * orizzontale e la sua linea di base cadono nell'area.
     */
    record Area(float x0, float x1, float y0, float y1) {

        static Area tutta() {
            return new Area(-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE);
        }

        boolean contiene(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    /** Riga di testo: caratteri con la stessa linea di base, in ordine orizzontale. */
    static final class Riga {

        private final List<Carattere> caratteri;
        private final float y;
        private final List<Parola> parole;
        private final String compatta;
        private final int[] indiceCompatto;

        Riga(List<Carattere> caratteri) {
            this.caratteri = caratteri.stream().sorted(Comparator.comparingDouble(Carattere::x)).toList();
            this.y = (float) this.caratteri.stream().mapToDouble(Carattere::y).average().orElse(0);
            this.parole = parole(this.caratteri);
            var testo = new StringBuilder();
            var indice = new ArrayList<Integer>();
            for (int i = 0; i < this.caratteri.size(); i++) {
                var c = this.caratteri.get(i);
                if (c.spazio()) {
                    continue;
                }
                var maiuscolo = c.testo().toUpperCase(Locale.ROOT);
                for (int k = 0; k < maiuscolo.length(); k++) {
                    testo.append(maiuscolo.charAt(k));
                    indice.add(i);
                }
            }
            this.compatta = testo.toString();
            this.indiceCompatto = indice.stream().mapToInt(Integer::intValue).toArray();
        }

        float y() {
            return y;
        }

        List<Carattere> caratteri() {
            return caratteri;
        }

        List<Parola> parole() {
            return parole;
        }

        String compatta() {
            return compatta;
        }

        /** Testo della riga con uno spazio tra le parole. */
        String testo() {
            return String.join(" ", parole.stream().map(Parola::testo).toList());
        }

        /** Etichette della riga che corrispondono al pattern (sul testo compatto, con confini di parola). */
        List<Etichetta> cerca(Pagina pagina, Pattern etichetta) {
            var trovate = new ArrayList<Etichetta>();
            var m = etichetta.matcher(compatta);
            int da = 0;
            while (da <= compatta.length() && m.find(da)) {
                if (m.end() > m.start()) {
                    var primo = caratteri.get(indiceCompatto[m.start()]);
                    var ultimo = caratteri.get(indiceCompatto[m.end() - 1]);
                    if (inizioParola(indiceCompatto[m.start()]) && fineParola(indiceCompatto[m.end() - 1])) {
                        trovate.add(new Etichetta(pagina, primo.x(), ultimo.x1(), y));
                    }
                }
                da = m.start() + 1;
            }
            return trovate;
        }

        private boolean inizioParola(int i) {
            return i == 0 || distaccati(caratteri.get(i - 1), caratteri.get(i));
        }

        private boolean fineParola(int i) {
            return i == caratteri.size() - 1 || distaccati(caratteri.get(i), caratteri.get(i + 1));
        }

        private static List<Parola> parole(List<Carattere> caratteri) {
            var parole = new ArrayList<Parola>();
            var testo = new StringBuilder();
            float x0 = 0;
            float x1 = 0;
            float y = 0;
            Carattere precedente = null;
            for (var c : caratteri) {
                if (c.spazio() || (precedente != null && distaccati(precedente, c))) {
                    if (!testo.isEmpty()) {
                        parole.add(new Parola(testo.toString(), x0, x1, y));
                        testo.setLength(0);
                    }
                }
                if (!c.spazio()) {
                    if (testo.isEmpty()) {
                        x0 = c.x();
                        y = c.y();
                    }
                    testo.append(c.testo());
                    x1 = Math.max(x1, c.x1());
                }
                precedente = c;
            }
            if (!testo.isEmpty()) {
                parole.add(new Parola(testo.toString(), x0, x1, y));
            }
            return List.copyOf(parole);
        }

        /** Due caratteri sono distaccati se uno e' uno spazio o se tra loro c'e' almeno un quarto di carattere. */
        static boolean distaccati(Carattere a, Carattere b) {
            if (a.spazio() || b.spazio()) {
                return true;
            }
            float riferimento = Math.max(1f, Math.min(a.altezza() > 0 ? a.altezza() : 6f, 12f));
            return b.x() - a.x1() > riferimento * 0.25f;
        }
    }

    /** Pagina: righe dall'alto in basso. */
    static final class Pagina {

        private final int numero;
        private final float larghezza;
        private final float altezza;
        private final List<Riga> righe;

        Pagina(int numero, float larghezza, float altezza, List<Carattere> caratteri) {
            this.numero = numero;
            this.larghezza = larghezza;
            this.altezza = altezza;
            this.righe = righe(caratteri);
        }

        int numero() {
            return numero;
        }

        float larghezza() {
            return larghezza;
        }

        float altezza() {
            return altezza;
        }

        List<Riga> righe() {
            return righe;
        }

        int caratteriSignificativi() {
            return righe.stream().mapToInt(r -> (int) r.compatta().chars().filter(Character::isLetterOrDigit).count())
                    .sum();
        }

        List<Etichetta> cerca(Pattern etichetta) {
            var trovate = new ArrayList<Etichetta>();
            righe.forEach(r -> trovate.addAll(r.cerca(this, etichetta)));
            return trovate;
        }

        Optional<Etichetta> prima(Pattern etichetta) {
            return cerca(etichetta).stream().findFirst();
        }

        /** Parole dell'area, dall'alto in basso e da sinistra a destra. */
        List<Parola> parole(Area area) {
            var trovate = new ArrayList<Parola>();
            for (var r : righe) {
                for (var p : r.parole()) {
                    if (area.contiene(p.centro(), p.y())) {
                        trovate.add(p);
                    }
                }
            }
            return trovate;
        }

        /** Parole dell'area che soddisfano il filtro. */
        List<Parola> parole(Area area, Predicate<String> filtro) {
            return parole(area).stream().filter(p -> filtro.test(p.testo())).toList();
        }

        /**
         * Testo dei caratteri dell'area (centro orizzontale nell'area), riga per riga, con uno spazio dove i caratteri
         * sono distaccati: serve per i campi stampati attaccati, che si separano con gli estremi delle colonne.
         */
        String testo(Area area) {
            var testo = new StringBuilder();
            for (var r : righe) {
                Carattere precedente = null;
                var riga = new StringBuilder();
                for (var c : r.caratteri()) {
                    if (!area.contiene(c.centro(), c.y())) {
                        continue;
                    }
                    if (precedente != null && Riga.distaccati(precedente, c) && !riga.isEmpty()
                            && riga.charAt(riga.length() - 1) != ' ') {
                        riga.append(' ');
                    }
                    if (!c.spazio()) {
                        riga.append(c.testo());
                    }
                    precedente = c;
                }
                var pulita = riga.toString().strip();
                if (!pulita.isEmpty()) {
                    if (!testo.isEmpty()) {
                        testo.append('\n');
                    }
                    testo.append(pulita);
                }
            }
            return testo.toString();
        }

        /** Righe la cui linea di base cade tra y0 e y1. */
        List<Riga> righe(float y0, float y1) {
            return righe.stream().filter(r -> r.y() >= y0 && r.y() <= y1).toList();
        }

        private static List<Riga> righe(List<Carattere> caratteri) {
            var ordinati = caratteri.stream()
                    .sorted(Comparator.comparingDouble(Carattere::y).thenComparingDouble(Carattere::x))
                    .toList();
            var righe = new ArrayList<Riga>();
            var corrente = new ArrayList<Carattere>();
            float yCorrente = Float.NaN;
            for (var c : ordinati) {
                if (!corrente.isEmpty() && Math.abs(c.y() - yCorrente) > TOLLERANZA_RIGA) {
                    righe.add(new Riga(corrente));
                    corrente = new ArrayList<>();
                }
                if (corrente.isEmpty()) {
                    yCorrente = c.y();
                }
                corrente.add(c);
            }
            if (!corrente.isEmpty()) {
                righe.add(new Riga(corrente));
            }
            return List.copyOf(righe);
        }
    }
}
