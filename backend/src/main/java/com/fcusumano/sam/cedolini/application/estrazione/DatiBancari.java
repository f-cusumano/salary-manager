package com.fcusumano.sam.cedolini.application.estrazione;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rete di sicurezza contro i dati bancari sfuggiti al prompt (e scritti nei testi liberi dell'inserimento a mano):
 * individua e maschera gli IBAN (ISO 13616) di qualunque paese in un testo libero, anche scritti a gruppi separati da
 * spazi di ogni tipo (anche lo spazio non separabile delle pagine web), trattini o punti, o con caratteri invisibili
 * in mezzo. Una sequenza e' un IBAN solo se ha la lunghezza
 * prevista per il paese e supera la verifica delle cifre di controllo (modulo 97): codici fiscali, matricole e codici
 * voce con una struttura simile non vengono toccati.
 */
final class DatiBancari {

    /** Testo che sostituisce l'IBAN. */
    static final String MASCHERA = "[dato bancario rimosso]";

    /**
     * Separatori ammessi tra i caratteri di un IBAN scritto a gruppi: spazi (anche Unicode), trattini, punti e
     * caratteri invisibili di formattazione.
     */
    private static final String SEPARATORE = "[\\s\\p{Z}\\u200B-\\u200D\\u2060\\uFEFF.\\-]";

    /** Inizio possibile di un IBAN: codice paese, cifre di controllo e almeno 11 caratteri (anche a gruppi). */
    private static final Pattern CANDIDATO =
            Pattern.compile("\\b[A-Za-z]{2}\\d{2}(?:" + SEPARATORE + "{0,3}[A-Za-z0-9]){11,30}");

    private static final int LUNGHEZZA_MINIMA = 15;
    private static final int LUNGHEZZA_MASSIMA = 34;

    /** Lunghezza dell'IBAN per paese (registro ISO 13616); per i paesi non elencati si provano tutte le lunghezze. */
    private static final Map<String, Integer> LUNGHEZZE = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AE", 23), Map.entry("AL", 28), Map.entry("AT", 20), Map.entry("AZ", 28),
            Map.entry("BA", 20), Map.entry("BE", 16), Map.entry("BG", 22), Map.entry("BH", 22), Map.entry("BR", 29),
            Map.entry("BY", 28), Map.entry("CH", 21), Map.entry("CR", 22), Map.entry("CY", 28), Map.entry("CZ", 24),
            Map.entry("DE", 22), Map.entry("DK", 18), Map.entry("DO", 28), Map.entry("EE", 20), Map.entry("EG", 29),
            Map.entry("ES", 24), Map.entry("FI", 18), Map.entry("FO", 18), Map.entry("FR", 27), Map.entry("GB", 22),
            Map.entry("GE", 22), Map.entry("GI", 23), Map.entry("GL", 18), Map.entry("GR", 27), Map.entry("GT", 28),
            Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22), Map.entry("IL", 23), Map.entry("IQ", 23),
            Map.entry("IS", 26), Map.entry("IT", 27), Map.entry("JO", 30), Map.entry("KW", 30), Map.entry("KZ", 20),
            Map.entry("LB", 28), Map.entry("LC", 32), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20),
            Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MD", 24), Map.entry("ME", 22), Map.entry("MK", 19),
            Map.entry("MR", 27), Map.entry("MT", 31), Map.entry("MU", 30), Map.entry("NL", 18), Map.entry("NO", 15),
            Map.entry("PK", 24), Map.entry("PL", 28), Map.entry("PS", 29), Map.entry("PT", 25), Map.entry("QA", 29),
            Map.entry("RO", 24), Map.entry("RS", 22), Map.entry("SA", 24), Map.entry("SC", 31), Map.entry("SE", 24),
            Map.entry("SI", 19), Map.entry("SK", 24), Map.entry("SM", 27), Map.entry("ST", 25), Map.entry("SV", 28),
            Map.entry("TL", 23), Map.entry("TN", 24), Map.entry("TR", 26), Map.entry("UA", 29), Map.entry("VA", 22),
            Map.entry("VG", 24), Map.entry("XK", 20));

    private DatiBancari() {
    }

    /** Testo con gli IBAN sostituiti da {@link #MASCHERA}; lo stesso oggetto se non ne contiene. */
    static String maschera(String testo) {
        if (testo == null) {
            return null;
        }
        var risultato = new StringBuilder();
        int copiatoFino = 0;
        int da = 0;
        var matcher = CANDIDATO.matcher(testo);
        while (da < testo.length() && matcher.find(da)) {
            int fine = fineIban(testo, matcher.start(), matcher.end());
            if (fine < 0) {
                da = matcher.start() + 1;
                continue;
            }
            risultato.append(testo, copiatoFino, matcher.start()).append(MASCHERA);
            copiatoFino = fine;
            da = fine;
        }
        if (copiatoFino == 0) {
            return testo;
        }
        return risultato.append(testo, copiatoFino, testo.length()).toString();
    }

    /**
     * Fine (esclusa) dell'IBAN che comincia in {@code inizio}, -1 se il candidato non e' un IBAN valido. Per un paese
     * noto vale solo la sua lunghezza, altrimenti la piu' lunga che supera la verifica.
     */
    private static int fineIban(String testo, int inizio, int fineCandidato) {
        var compatto = new StringBuilder();
        var fineCarattere = new int[LUNGHEZZA_MASSIMA];
        for (int i = inizio; i < fineCandidato && compatto.length() < LUNGHEZZA_MASSIMA; i++) {
            char c = testo.charAt(i);
            if (alfanumerico(c)) {
                compatto.append(Character.toUpperCase(c));
                fineCarattere[compatto.length() - 1] = i + 1;
            }
        }
        var paese = compatto.substring(0, 2);
        Integer prevista = LUNGHEZZE.get(paese);
        int massima = prevista != null ? prevista : compatto.length();
        int minima = prevista != null ? prevista : LUNGHEZZA_MINIMA;
        for (int lunghezza = Math.min(massima, compatto.length()); lunghezza >= minima; lunghezza--) {
            int fine = fineCarattere[lunghezza - 1];
            boolean delimitato = fine == testo.length() || !Character.isLetterOrDigit(testo.charAt(fine));
            if (delimitato && controlloValido(compatto.substring(0, lunghezza))) {
                return fine;
            }
        }
        return -1;
    }

    /** Carattere di un IBAN (il candidato contiene solo questi e separatori). */
    private static boolean alfanumerico(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** Verifica ISO 13616: primi 4 caratteri in fondo, lettere come 10..35, resto modulo 97 uguale a 1. */
    static boolean controlloValido(String iban) {
        var normalizzato = iban.toUpperCase(Locale.ROOT);
        int cifreControllo = Integer.parseInt(normalizzato.substring(2, 4));
        if (cifreControllo < 2 || cifreControllo > 98) {
            return false;
        }
        var riordinato = normalizzato.substring(4) + normalizzato.substring(0, 4);
        int resto = 0;
        for (int i = 0; i < riordinato.length(); i++) {
            int valore = Character.digit(riordinato.charAt(i), 36);
            resto = (valore < 10 ? resto * 10 + valore : resto * 100 + valore) % 97;
        }
        return resto == 1;
    }
}
