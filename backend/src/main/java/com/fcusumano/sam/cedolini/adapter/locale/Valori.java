package com.fcusumano.sam.cedolini.adapter.locale;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Interpretazione dei valori stampati sui cedolini: importi all'italiana ("1.234,56", "305,73-", "28216,55"), date
 * ("15/01/1985", "6/06/2022", "15-01-1985", "15011985"), mese e anno ("042022", "4-2022", "5/2024", "Giugno 2024") e
 * codici (codice fiscale, partita IVA, posizione INPS). Funzioni pure: un valore non interpretabile da' empty, mai un
 * valore inventato.
 */
final class Valori {

    /** Importo all'italiana: migliaia con il punto (facoltative), decimali con la virgola, segno meno finale o iniziale. */
    static final Pattern IMPORTO = Pattern.compile("-?(?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d+-?");
    static final Pattern INTERO = Pattern.compile("\\d+");
    static final Pattern CODICE_FISCALE =
            Pattern.compile("[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]");
    static final Pattern UNDICI_CIFRE = Pattern.compile("\\d{11}");
    static final Pattern DATA = Pattern.compile("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})");
    static final Pattern MESE_ANNO = Pattern.compile("(\\d{1,2})[/.\\-](\\d{4})");
    static final Pattern SEI_CIFRE = Pattern.compile("(\\d{2})(\\d{4})");

    static final Map<String, Integer> MESI = Map.ofEntries(
            Map.entry("GENNAIO", 1), Map.entry("FEBBRAIO", 2), Map.entry("MARZO", 3), Map.entry("APRILE", 4),
            Map.entry("MAGGIO", 5), Map.entry("GIUGNO", 6), Map.entry("LUGLIO", 7), Map.entry("AGOSTO", 8),
            Map.entry("SETTEMBRE", 9), Map.entry("OTTOBRE", 10), Map.entry("NOVEMBRE", 11), Map.entry("DICEMBRE", 12));

    private Valori() {
    }

    static boolean importo(String testo) {
        return testo != null && IMPORTO.matcher(testo.strip()).matches();
    }

    /** Importo stampato ("1.234,56", "1234,56", "305,73-", "-0,01"); empty se non e' un importo. */
    static Optional<BigDecimal> leggiImporto(String testo) {
        if (!importo(testo)) {
            return Optional.empty();
        }
        var v = testo.strip();
        boolean negativo = v.startsWith("-") || v.endsWith("-");
        v = v.replace("-", "").replace(".", "").replace(',', '.');
        var numero = new BigDecimal(v);
        return Optional.of(negativo ? numero.negate() : numero);
    }

    /** Numero intero o decimale stampato senza separatori ("5", "26", "176,00"). */
    static Optional<BigDecimal> leggiNumero(String testo) {
        if (testo == null) {
            return Optional.empty();
        }
        var v = testo.strip();
        if (INTERO.matcher(v).matches()) {
            return Optional.of(new BigDecimal(v));
        }
        return leggiImporto(v);
    }

    /** Data stampata con separatori ("15/01/1985", "6/06/2022", "15-01-1985"). */
    static Optional<LocalDate> leggiData(String testo) {
        if (testo == null) {
            return Optional.empty();
        }
        var m = DATA.matcher(testo.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return data(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }

    /** Data compatta di 8 cifre ggmmaaaa ("15011985"). */
    static Optional<LocalDate> leggiDataCompatta(String testo) {
        if (testo == null || !testo.matches("\\d{8}")) {
            return Optional.empty();
        }
        return data(Integer.parseInt(testo.substring(4)), Integer.parseInt(testo.substring(2, 4)),
                Integer.parseInt(testo.substring(0, 2)));
    }

    /** Mese e anno ("042022", "4-2022", "5/2024", "11/2021"). */
    static Optional<YearMonth> leggiMeseAnno(String testo) {
        if (testo == null) {
            return Optional.empty();
        }
        var v = testo.strip();
        var m = MESE_ANNO.matcher(v);
        if (!m.matches()) {
            m = SEI_CIFRE.matcher(v);
            if (!m.matches()) {
                return Optional.empty();
            }
        }
        int mese = Integer.parseInt(m.group(1));
        int anno = Integer.parseInt(m.group(2));
        return mese >= 1 && mese <= 12 ? Optional.of(YearMonth.of(anno, mese)) : Optional.empty();
    }

    /** Mese scritto in lettere ("Giugno", "DICEMBRE"); empty se non e' un mese. */
    static Optional<Integer> mese(String testo) {
        return testo == null ? Optional.empty()
                : Optional.ofNullable(MESI.get(testo.strip().toUpperCase(Locale.ROOT)));
    }

    /** Codice fiscale di persona fisica (16 caratteri, formalmente plausibile). */
    static boolean codiceFiscale(String testo) {
        return testo != null && CODICE_FISCALE.matcher(testo).matches();
    }

    /** Importo in formato JSON dello schema ("1234.56"): stringa vuota se null. */
    static String json(BigDecimal importo) {
        return importo == null ? "" : importo.toPlainString();
    }

    private static Optional<LocalDate> data(int anno, int mese, int giorno) {
        try {
            return Optional.of(LocalDate.of(anno, mese, giorno));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
