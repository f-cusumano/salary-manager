package com.fcusumano.sam.ccnl;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Termini di preavviso di un CCNL per un gruppo di livelli e una fascia di anzianita' (dato di riferimento, inserito
 * solo se verificato su due fonti concordi). I termini sono in mesi e giorni di calendario.
 *
 * @param livelli            codici dei livelli del gruppo, normalizzati con {@link #normalizzaLivello(String)}
 * @param descrizioneLivelli descrizione del gruppo come nel CCNL (es. "Quadri e 1° livello")
 * @param oltreAnni          la regola vale per un'anzianita' superiore a questi anni (0, 5, 10): "fino a 5 anni" e'
 *                           0, "oltre 5 e fino a 10" e' 5, "oltre 10" e' 10
 * @param dimissioni         preavviso dovuto dal lavoratore che si dimette
 * @param licenziamento      preavviso dovuto dal datore di lavoro che licenzia
 * @param riferimento        articolo del CCNL e fonti
 */
public record RegolaPreavviso(
        Set<String> livelli,
        String descrizioneLivelli,
        int oltreAnni,
        DurataPreavviso dimissioni,
        DurataPreavviso licenziamento,
        String riferimento) {

    private static final Pattern ROMANO = Pattern.compile("^(VIII|VII|VI|IV|V|III|II|I)(S?)$");
    private static final String[] ROMANI = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    public RegolaPreavviso {
        livelli = Set.copyOf(livelli);
        Objects.requireNonNull(dimissioni, "dimissioni");
        Objects.requireNonNull(licenziamento, "licenziamento");
        if (oltreAnni < 0) {
            throw new IllegalArgumentException("oltreAnni negativo: " + oltreAnni);
        }
    }

    /**
     * Livello come stampato ridotto a un codice confrontabile: maiuscole, senza "livello"/"categoria" e punteggiatura,
     * "quadro" = Q, "super" = S, numeri romani in cifre. Es. "3° Super" = "3S", "Quadro A" = "QA", "VI liv." = "6",
     * "C3" = "C3". Null o vuoto restano null.
     */
    public static String normalizzaLivello(String livello) {
        if (livello == null) {
            return null;
        }
        var testo = livello.toUpperCase(Locale.ROOT)
                .replaceAll("QUADR[OI]", "Q")
                .replaceAll("SUPER(IORE)?", "S")
                .replaceAll("LIVELLO|LIV\\.?|CATEGORIA|CAT\\.?|CATEG\\.?", "")
                .replaceAll("[^A-Z0-9]", "");
        if (testo.isEmpty()) {
            return null;
        }
        var romano = ROMANO.matcher(testo);
        if (romano.matches()) {
            for (int i = 0; i < ROMANI.length; i++) {
                if (ROMANI[i].equals(romano.group(1))) {
                    return (i + 1) + romano.group(2);
                }
            }
        }
        return testo;
    }

    /** True se il livello stampato (normalizzato) appartiene al gruppo. */
    public boolean comprende(String livello) {
        var codice = normalizzaLivello(livello);
        return codice != null && livelli.contains(codice);
    }
}
