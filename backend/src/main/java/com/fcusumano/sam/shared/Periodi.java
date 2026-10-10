package com.fcusumano.sam.shared;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Utilita' minime su mesi e periodi.
 */
public final class Periodi {

    private static final String[] NOMI_MESI = {
            "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
            "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    };

    private Periodi() {
    }

    /** Numero di mesi tra due mesi, estremi compresi (gennaio-gennaio = 1). */
    public static int mesiInclusi(YearMonth da, YearMonth a) {
        if (a.isBefore(da)) {
            throw new IllegalArgumentException("Periodo non valido: " + da + " > " + a);
        }
        return (a.getYear() - da.getYear()) * 12 + (a.getMonthValue() - da.getMonthValue()) + 1;
    }

    public static YearMonth meseDi(LocalDate data) {
        return data == null ? null : YearMonth.from(data);
    }

    public static LocalDate primoGiorno(YearMonth mese) {
        return mese == null ? null : mese.atDay(1);
    }

    /** Nome del mese in italiano con iniziale maiuscola (1 = "Gennaio"). */
    public static String nomeMese(int mese) {
        if (mese < 1 || mese > 12) {
            throw new IllegalArgumentException("Mese non valido: " + mese);
        }
        return NOMI_MESI[mese - 1];
    }

    /** Descrizione estesa di un mese, es. "Gennaio 2024". */
    public static String descrizione(YearMonth mese) {
        return nomeMese(mese.getMonthValue()) + " " + mese.getYear();
    }
}
