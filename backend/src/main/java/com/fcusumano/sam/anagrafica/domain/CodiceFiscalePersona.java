package com.fcusumano.sam.anagrafica.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Codice fiscale di una persona fisica: normalizzazione e controllo formale (struttura e carattere di controllo,
 * omocodie comprese).
 */
public final class CodiceFiscalePersona {

    /** Lunghezza massima memorizzabile (colonna {@code persona.codice_fiscale}). */
    public static final int LUNGHEZZA = 16;

    private static final Pattern SPAZI = Pattern.compile("\\s+");
    /** Cifre o lettere sostitutive delle omocodie (L=0, M=1, N=2, P=3, Q=4, R=5, S=6, T=7, U=8, V=9). */
    private static final String CIFRA = "[0-9LMNPQRSTUV]";
    private static final Pattern FORMATO = Pattern.compile(
            "[A-Z]{6}" + CIFRA + "{2}[ABCDEHLMPRST]" + CIFRA + "{2}[A-Z]" + CIFRA + "{3}[A-Z]");

    /** Valori dei caratteri in posizione dispari (1a, 3a, ...), indicizzati per cifra 0-9 e lettera A-Z. */
    private static final int[] DISPARI_CIFRE = {1, 0, 5, 7, 9, 13, 15, 17, 19, 21};
    private static final int[] DISPARI_LETTERE = {
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23};

    private CodiceFiscalePersona() {
    }

    /** Maiuscolo e senza spazi (anche interni); null o vuoto diventa null. */
    public static String normalizza(String codiceFiscale) {
        if (codiceFiscale == null) {
            return null;
        }
        var normalizzato = SPAZI.matcher(codiceFiscale).replaceAll("").toUpperCase(Locale.ROOT);
        return normalizzato.isEmpty() ? null : normalizzato;
    }

    /** True se il codice (gia' normalizzato) ha la struttura di un codice fiscale e il carattere di controllo torna. */
    public static boolean formalmenteValido(String codiceNormalizzato) {
        if (codiceNormalizzato == null || !FORMATO.matcher(codiceNormalizzato).matches()) {
            return false;
        }
        return codiceNormalizzato.charAt(15) == carattereDiControllo(codiceNormalizzato.substring(0, 15));
    }

    /** Carattere di controllo dei primi 15 caratteri di un codice fiscale maiuscolo. */
    static char carattereDiControllo(String primiQuindici) {
        int somma = 0;
        for (int i = 0; i < primiQuindici.length(); i++) {
            char c = primiQuindici.charAt(i);
            boolean dispari = i % 2 == 0;
            if (Character.isDigit(c)) {
                somma += dispari ? DISPARI_CIFRE[c - '0'] : c - '0';
            } else {
                somma += dispari ? DISPARI_LETTERE[c - 'A'] : c - 'A';
            }
        }
        return (char) ('A' + somma % 26);
    }
}
