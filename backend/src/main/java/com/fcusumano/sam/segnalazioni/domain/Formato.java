package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.Periodi;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Formattazione dei valori nei testi delle segnalazioni: importi in formato italiano ("1.234,56 €"), mesi e date con
 * il mese per esteso ("Gennaio 2024", "15 Giugno 2024"), competenze ("Tredicesima 2023",
 * "Fine rapporto 1 – Luglio 2024").
 */
public final class Formato {

    private static final DecimalFormatSymbols SIMBOLI_ITALIANI = DecimalFormatSymbols.getInstance(Locale.ITALY);

    private Formato() {
    }

    /** Importo in euro con due decimali, es. "1.234,56 €" o "-12,30 €". */
    public static String euro(BigDecimal importo) {
        // DecimalFormat non e' thread-safe: un'istanza per chiamata
        var formato = new DecimalFormat("#,##0.00", SIMBOLI_ITALIANI);
        formato.setRoundingMode(Importi.ARROTONDAMENTO);
        return formato.format(importo) + " €";
    }

    /** Mese per esteso, es. "Gennaio 2024". */
    public static String mese(YearMonth mese) {
        return Periodi.descrizione(mese);
    }

    /** Nome del mese senza anno, es. "Luglio". */
    public static String nomeMese(int mese) {
        return Periodi.nomeMese(mese);
    }

    /** Data con il mese per esteso, es. "15 Giugno 2024". */
    public static String data(LocalDate data) {
        return data.getDayOfMonth() + " " + Periodi.nomeMese(data.getMonthValue()) + " " + data.getYear();
    }

    /** Fine dell'anno nel formato usato sui cedolini per il fondo TFR, es. "31/12/2023". */
    public static String fineAnno(int anno) {
        return "31/12/" + anno;
    }

    /** Competenza leggibile: "Gennaio 2024", "Tredicesima 2024", "Quattordicesima 2024", "Fine rapporto 1 – Luglio 2024". */
    public static String competenza(Competenza competenza) {
        return switch (competenza) {
            case Competenza.Mensilita m when m.mese() == 13 -> "Tredicesima " + m.anno();
            case Competenza.Mensilita m when m.mese() == 14 -> "Quattordicesima " + m.anno();
            case Competenza.Mensilita m -> mese(m.comeYearMonth());
            case Competenza.FineRapporto f -> "Fine rapporto " + f.progressivo() + " – " + mese(f.periodoRetribuzione());
        };
    }
}
