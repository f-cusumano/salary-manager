package com.fcusumano.sam.anagrafica.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilita' sui testi letti dai cedolini.
 */
public final class Testi {

    private static final Pattern SPAZI = Pattern.compile("\\s+");
    private static final Pattern SEGNI_DIACRITICI = Pattern.compile("\\p{M}+");

    private Testi() {
    }

    /** Toglie gli spazi ai bordi e riduce quelli interni a uno; null o vuoto diventa null. */
    public static String pulisci(String testo) {
        if (testo == null) {
            return null;
        }
        var pulito = SPAZI.matcher(testo.strip()).replaceAll(" ");
        return pulito.isEmpty() ? null : pulito;
    }

    /** Come {@link #pulisci(String)}, poi tronca alla lunghezza massima della colonna. */
    public static String pulisciETronca(String testo, int lunghezzaMassima) {
        var pulito = pulisci(testo);
        if (pulito == null || pulito.length() <= lunghezzaMassima) {
            return pulito;
        }
        return pulito.substring(0, lunghezzaMassima).strip();
    }

    /** Maiuscolo senza accenti (es. "Società" diventa "SOCIETA"). */
    public static String maiuscoloSenzaAccenti(String testo) {
        var scomposto = Normalizer.normalize(testo, Normalizer.Form.NFD);
        return SEGNI_DIACRITICI.matcher(scomposto).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
