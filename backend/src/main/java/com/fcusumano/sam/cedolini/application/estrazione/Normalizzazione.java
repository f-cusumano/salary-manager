package com.fcusumano.sam.cedolini.application.estrazione;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizzazione dei valori estratti: testi, importi, date e periodi. Il modello normalizza gia' i formati stampati;
 * qui si tollerano le varianti residue (virgola decimale, date italiane) e si scartano i valori non interpretabili.
 * Funzioni pure, senza stato.
 */
final class Normalizzazione {

    private static final Pattern SPAZI = Pattern.compile("\\s+");
    private static final Pattern OTTO_CIFRE = Pattern.compile("\\d{8}");
    private static final Pattern SEI_CIFRE = Pattern.compile("\\d{6}");
    private static final Pattern GIORNO_MESE_ANNO = Pattern.compile("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})");
    private static final Pattern MESE_ANNO = Pattern.compile("(\\d{1,2})[/.\\-](\\d{4})");
    private static final Pattern ANNO_MESE = Pattern.compile("(\\d{4})-(\\d{1,2})");
    private static final DateTimeFormatter GGMMAAAA =
            DateTimeFormatter.ofPattern("ddMMuuuu", Locale.ITALY).withResolverStyle(ResolverStyle.STRICT);

    private Normalizzazione() {
    }

    /** Testo ripulito (spazi multipli ridotti, trim); vuoto -> empty. */
    static Optional<String> testo(String valore) {
        if (valore == null) {
            return Optional.empty();
        }
        var pulito = SPAZI.matcher(valore).replaceAll(" ").strip();
        return pulito.isEmpty() ? Optional.empty() : Optional.of(pulito);
    }

    /** Codice (CF, P.IVA, posizioni): senza spazi e in maiuscolo. */
    static Optional<String> codice(String valore) {
        return testo(valore).map(v -> SPAZI.matcher(v).replaceAll("").toUpperCase(Locale.ROOT));
    }

    /** Partita IVA senza prefisso nazionale "IT". */
    static Optional<String> partitaIva(String valore) {
        return codice(valore).map(v -> v.startsWith("IT") && v.length() == 13 ? v.substring(2) : v);
    }

    /**
     * Importo decimale. Accetta il formato richiesto ("1234.56") e le varianti italiane ("1.234,56", "1234,56",
     * "123,45-"); empty se vuoto.
     *
     * @throws NumberFormatException se il valore non e' interpretabile
     */
    static Optional<BigDecimal> importo(String valore) {
        var v = codice(valore).map(s -> s.replace("€", "").replace("EUR", "")).orElse("");
        if (v.isEmpty()) {
            return Optional.empty();
        }
        boolean negativo = false;
        if (v.endsWith("-")) {
            negativo = true;
            v = v.substring(0, v.length() - 1);
        }
        if (v.startsWith("+")) {
            v = v.substring(1);
        }
        int virgola = v.lastIndexOf(',');
        int punto = v.lastIndexOf('.');
        if (virgola >= 0 && punto >= 0) {
            v = virgola > punto ? v.replace(".", "").replace(',', '.') : v.replace(",", "");
        } else if (virgola >= 0) {
            v = v.indexOf(',') == virgola ? v.replace(',', '.') : v.replace(",", "");
        } else if (punto >= 0 && v.indexOf('.') != punto) {
            v = v.replace(".", "");
        }
        var numero = new BigDecimal(v);
        return Optional.of(negativo ? numero.negate() : numero);
    }

    /**
     * Data normalizzata in ISO yyyy-MM-dd. Accetta ISO, "g/m/aaaa", "g-m-aaaa", "ggmmaaaa", e i formati mese/anno
     * ("mmaaaa", "m-aaaa", "aaaa-mm") che diventano il primo giorno del mese.
     *
     * @throws DateTimeException se il valore non e' una data valida
     */
    static Optional<LocalDate> data(String valore) {
        var v = codice(valore).orElse("");
        if (v.isEmpty()) {
            return Optional.empty();
        }
        if (v.length() == 10 && v.charAt(4) == '-') {
            return Optional.of(LocalDate.parse(v));
        }
        if (OTTO_CIFRE.matcher(v).matches()) {
            return Optional.of(LocalDate.parse(v, GGMMAAAA));
        }
        if (SEI_CIFRE.matcher(v).matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(v.substring(2)), Integer.parseInt(v.substring(0, 2))).atDay(1));
        }
        var gma = GIORNO_MESE_ANNO.matcher(v);
        if (gma.matches()) {
            return Optional.of(LocalDate.of(Integer.parseInt(gma.group(3)), Integer.parseInt(gma.group(2)),
                    Integer.parseInt(gma.group(1))));
        }
        return meseAnno(v).map(periodo -> periodo.atDay(1));
    }

    /**
     * Mese/anno ("aaaa-mm", "mm/aaaa", "m-aaaa", "mmaaaa").
     *
     * @throws DateTimeException se il mese non e' valido o il formato non e' riconosciuto
     */
    static Optional<YearMonth> periodo(String valore) {
        var v = codice(valore).orElse("");
        if (v.isEmpty()) {
            return Optional.empty();
        }
        if (SEI_CIFRE.matcher(v).matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(v.substring(2)), Integer.parseInt(v.substring(0, 2))));
        }
        return meseAnno(v);
    }

    private static Optional<YearMonth> meseAnno(String v) {
        var am = ANNO_MESE.matcher(v);
        if (am.matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(am.group(1)), Integer.parseInt(am.group(2))));
        }
        var ma = MESE_ANNO.matcher(v);
        if (ma.matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(ma.group(2)), Integer.parseInt(ma.group(1))));
        }
        throw new DateTimeException("Formato data non riconosciuto");
    }

    /**
     * Intero (es. numero scatti) da testo; accetta "2" e "2,00".
     *
     * @throws NumberFormatException  se non numerico
     * @throws ArithmeticException    se non intero
     */
    static Optional<Integer> intero(String valore) {
        return importo(valore).map(BigDecimal::stripTrailingZeros).map(BigDecimal::intValueExact);
    }

    /** "SI"/"NO" -> true/false, altrimenti empty. */
    static Optional<Boolean> siNo(String valore) {
        return switch (valore == null ? "" : valore) {
            case "SI" -> Optional.of(Boolean.TRUE);
            case "NO" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    /** Lista di testi non vuoti, ripuliti e senza duplicati. */
    static List<String> testi(List<String> valori) {
        return valori.stream().map(Normalizzazione::testo).flatMap(Optional::stream).distinct().toList();
    }
}
