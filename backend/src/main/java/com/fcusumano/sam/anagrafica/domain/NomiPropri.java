package com.fcusumano.sam.anagrafica.domain;

import java.util.Locale;

/**
 * Normalizzazione di nomi e cognomi stampati sui cedolini.
 */
public final class NomiPropri {

    private NomiPropri() {
    }

    /**
     * Spazi uniformati; se il testo e' tutto maiuscolo o tutto minuscolo (come spesso sui cedolini) diventa
     * "Iniziali Maiuscole", anche dopo apostrofo e trattino ("D'ANGELO" diventa "D'Angelo"). Un testo gia' in forma
     * mista resta com'e'.
     */
    public static String normalizza(String nome) {
        var pulito = Testi.pulisci(nome);
        if (pulito == null) {
            return null;
        }
        boolean tuttoMaiuscolo = pulito.equals(pulito.toUpperCase(Locale.ROOT));
        boolean tuttoMinuscolo = pulito.equals(pulito.toLowerCase(Locale.ROOT));
        if (!tuttoMaiuscolo && !tuttoMinuscolo) {
            return pulito;
        }
        var risultato = new StringBuilder(pulito.length());
        boolean inizioParola = true;
        for (char c : pulito.toLowerCase(Locale.ROOT).toCharArray()) {
            risultato.append(inizioParola ? Character.toUpperCase(c) : c);
            inizioParola = c == ' ' || c == '\'' || c == '-' || c == '’';
        }
        return risultato.toString();
    }
}
