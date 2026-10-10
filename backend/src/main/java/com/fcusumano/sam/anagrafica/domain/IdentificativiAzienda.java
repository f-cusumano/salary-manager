package com.fcusumano.sam.anagrafica.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizzazione degli identificativi di un'azienda: codice fiscale, partita IVA e matricola INPS.
 */
public final class IdentificativiAzienda {

    /** Lunghezza massima del codice fiscale (colonna {@code azienda.codice_fiscale}). */
    public static final int LUNGHEZZA_CODICE_FISCALE = 16;
    /** Lunghezza della partita IVA (colonna {@code azienda.partita_iva}). */
    public static final int LUNGHEZZA_PARTITA_IVA = 11;
    /** Lunghezza della matricola aziendale INPS. */
    public static final int LUNGHEZZA_MATRICOLA_INPS = 10;
    /** Lunghezza massima della posizione INPS memorizzabile (colonna {@code azienda.posizione_inps}). */
    public static final int LUNGHEZZA_POSIZIONE_INPS = 30;

    private static final Pattern SEPARATORI = Pattern.compile("[\\s.\\-/_]+");
    private static final Pattern UNDICI_CIFRE = Pattern.compile("[0-9]{11}");
    private static final Pattern PREFISSO_IT = Pattern.compile("IT([0-9]{11})");
    private static final Pattern GRUPPI_DI_CIFRE = Pattern.compile("[0-9]+");
    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^A-Z0-9]+");

    private IdentificativiAzienda() {
    }

    /**
     * Codice fiscale dell'azienda: maiuscolo, senza spazi e separatori, senza prefisso "IT" davanti a 11 cifre.
     * Restituisce null se vuoto o piu' lungo di 16 caratteri (non utilizzabile come chiave).
     */
    public static String normalizzaCodiceFiscale(String codiceFiscale) {
        var pulito = pulisciFiscale(codiceFiscale);
        return pulito == null || pulito.length() > LUNGHEZZA_CODICE_FISCALE ? null : pulito;
    }

    /**
     * Partita IVA: come il codice fiscale; restituisce null se il risultato non e' di 11 caratteri.
     */
    public static String normalizzaPartitaIva(String partitaIva) {
        var pulito = pulisciFiscale(partitaIva);
        return pulito == null || pulito.length() != LUNGHEZZA_PARTITA_IVA ? null : pulito;
    }

    /** True se il codice fiscale e' numerico di 11 cifre, cioe' coincide con una partita IVA. */
    public static boolean codiceFiscaleNumerico(String codiceFiscaleNormalizzato) {
        return codiceFiscaleNormalizzato != null && UNDICI_CIFRE.matcher(codiceFiscaleNormalizzato).matches();
    }

    /**
     * Chiave della posizione INPS. Alcuni layout stampano la matricola INPS (10 cifre) concatenata alla posizione
     * INAIL, con o senza separatori: si usa il primo gruppo di esattamente 10 cifre; in mancanza, le prime 10 cifre di
     * un gruppo piu' lungo; con meno di 10 cifre, tutte le cifre presenti; senza cifre, il testo alfanumerico maiuscolo.
     * Restituisce null se vuota.
     */
    public static String normalizzaPosizioneInps(String posizioneInps) {
        if (posizioneInps == null || posizioneInps.isBlank()) {
            return null;
        }
        var gruppi = new ArrayList<String>();
        var matcher = GRUPPI_DI_CIFRE.matcher(posizioneInps);
        while (matcher.find()) {
            gruppi.add(matcher.group());
        }
        var matricola = gruppi.stream().filter(g -> g.length() == LUNGHEZZA_MATRICOLA_INPS).findFirst()
                .or(() -> gruppi.stream().filter(g -> g.length() > LUNGHEZZA_MATRICOLA_INPS).findFirst()
                        .map(g -> g.substring(0, LUNGHEZZA_MATRICOLA_INPS)));
        if (matricola.isPresent()) {
            return matricola.get();
        }
        var cifre = String.join("", gruppi);
        if (cifre.length() >= LUNGHEZZA_MATRICOLA_INPS) {
            return cifre.substring(0, LUNGHEZZA_MATRICOLA_INPS);
        }
        if (!cifre.isEmpty()) {
            return cifre;
        }
        var alfanumerico = NON_ALFANUMERICI.matcher(Testi.maiuscoloSenzaAccenti(posizioneInps)).replaceAll("");
        if (alfanumerico.isEmpty()) {
            return null;
        }
        return alfanumerico.length() > LUNGHEZZA_POSIZIONE_INPS
                ? alfanumerico.substring(0, LUNGHEZZA_POSIZIONE_INPS)
                : alfanumerico;
    }

    /** Identificativi fiscali (codice fiscale e partita IVA, gia' normalizzati) confrontabili in modo incrociato. */
    public static Set<String> fiscali(String codiceFiscale, String partitaIva) {
        var insieme = new LinkedHashSet<String>();
        if (codiceFiscale != null) {
            insieme.add(codiceFiscale);
        }
        if (partitaIva != null) {
            insieme.add(partitaIva);
        }
        return insieme;
    }

    /** Valori di tipo partita IVA: la partita IVA e il codice fiscale numerico di 11 cifre. */
    static Set<String> numeriPartitaIva(String codiceFiscale, String partitaIva) {
        var insieme = new LinkedHashSet<String>();
        if (partitaIva != null) {
            insieme.add(partitaIva);
        }
        if (codiceFiscaleNumerico(codiceFiscale)) {
            insieme.add(codiceFiscale);
        }
        return insieme;
    }

    private static String pulisciFiscale(String valore) {
        if (valore == null) {
            return null;
        }
        var pulito = SEPARATORI.matcher(valore).replaceAll("").toUpperCase(Locale.ROOT);
        var conPrefisso = PREFISSO_IT.matcher(pulito);
        if (conPrefisso.matches()) {
            pulito = conPrefisso.group(1);
        }
        return pulito.isEmpty() ? null : pulito;
    }
}
