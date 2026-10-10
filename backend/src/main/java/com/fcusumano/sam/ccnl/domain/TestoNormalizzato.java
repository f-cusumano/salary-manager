package com.fcusumano.sam.ccnl.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Testo ridotto a sequenza di parole confrontabili: minuscolo, senza accenti, punteggiatura sostituita da spazi.
 * Esempio: "Metalmeccanici imp. (p.ind)" diventa [metalmeccanici, imp, p, ind].
 */
public record TestoNormalizzato(List<String> parole) {

    private static final Pattern SEGNI_DIACRITICI = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^a-z0-9]+");

    public TestoNormalizzato {
        parole = List.copyOf(parole);
    }

    public static TestoNormalizzato di(String testo) {
        if (testo == null || testo.isBlank()) {
            return new TestoNormalizzato(List.of());
        }
        var senzaAccenti = SEGNI_DIACRITICI.matcher(Normalizer.normalize(testo, Normalizer.Form.NFD)).replaceAll("");
        var pulito = NON_ALFANUMERICI.matcher(senzaAccenti.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return new TestoNormalizzato(pulito.isEmpty() ? List.of() : List.of(pulito.split(" ")));
    }

    public boolean vuoto() {
        return parole.isEmpty();
    }

    /** Posizioni (indice della prima parola) in cui la frase compare come sequenza di parole intere. */
    public List<Integer> occorrenze(TestoNormalizzato frase) {
        var risultato = new ArrayList<Integer>();
        var cercate = frase.parole();
        if (cercate.isEmpty()) {
            return risultato;
        }
        for (int i = 0; i + cercate.size() <= parole.size(); i++) {
            if (parole.subList(i, i + cercate.size()).equals(cercate)) {
                risultato.add(i);
            }
        }
        return risultato;
    }

    public boolean contiene(TestoNormalizzato frase) {
        return !occorrenze(frase).isEmpty();
    }
}
