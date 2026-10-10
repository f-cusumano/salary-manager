package com.fcusumano.sam.cedolini.adapter.locale;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Interpretazione comune ai layout del periodo di retribuzione stampato ("Giugno 2024", "Dicembre 2021 AGG.",
 * "13° MENSILITA' 2022", "04/2020 - Aprile") e degli elementi fissi della retribuzione dalle loro etichette.
 */
final class Periodo {

    private static final Pattern ANNO = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern AGGIUNTIVO = Pattern.compile("\\bAGG\\b");

    private Periodo() {
    }

    /** Periodo, intestazione di tredicesima/quattordicesima e cedolino aggiuntivo dal testo stampato. */
    static void leggi(String testo, CedolinoLetto c) {
        if (testo == null || testo.isBlank()) {
            return;
        }
        var t = testo.toUpperCase(Locale.ROOT);
        Integer anno = null;
        Integer mese = null;
        var meseAnno = Valori.MESE_ANNO.matcher(t);
        if (meseAnno.find()) {
            var letto = Valori.leggiMeseAnno(meseAnno.group());
            if (letto.isPresent()) {
                anno = letto.get().getYear();
                mese = letto.get().getMonthValue();
            }
        }
        if (anno == null) {
            var a = ANNO.matcher(t);
            while (a.find()) {
                anno = Integer.valueOf(a.group());
            }
        }
        if (mese == null) {
            for (var parola : t.split("[^A-Z]+")) {
                var m = Valori.mese(parola);
                if (m.isPresent()) {
                    mese = m.get();
                    break;
                }
            }
        }
        if (Classificazione.tredicesima(t)) {
            c.intestazioneTredicesima = true;
            if (mese == null) {
                mese = 12;
                c.nota("Intestazione di tredicesima mensilita' senza mese: periodo di dicembre");
            }
        } else if (Classificazione.quattordicesima(t)) {
            c.intestazioneQuattordicesima = true;
            if (mese == null && anno != null && c.dataElaborazione != null && c.dataElaborazione.getYear() == anno) {
                mese = c.dataElaborazione.getMonthValue();
                c.nota("Intestazione di quattordicesima mensilita' senza mese: usato il mese di elaborazione");
            }
        }
        if (AGGIUNTIVO.matcher(t).find()) {
            c.intestazioneAggiuntiva = true;
        }
        if (anno != null && mese != null) {
            c.periodo = YearMonth.of(anno, mese);
        }
    }

    /**
     * Elemento fisso della retribuzione dalla sua etichetta compatta ("MINIMO", "SCATTIANZ.", "SUP.ASS.",
     * "SUPERMINIMO", "CONTINGENZA"); le etichette non riconosciute finiscono negli altri elementi fissi.
     */
    static void elemento(CedolinoLetto c, String etichetta, BigDecimal valore) {
        var e = etichetta.toUpperCase(Locale.ROOT).replace(" ", "");
        if (e.equals("MINIMO") || e.startsWith("MINIMOTAB") || e.startsWith("PAGABASE")) {
            c.minimo = valore;
        } else if (e.startsWith("CONTING")) {
            c.contingenza = valore;
        } else if (e.startsWith("SCATT")) {
            c.importoScatti = valore;
        } else if (e.startsWith("SUP") && (e.contains("NONASS") || e.contains("N.ASS"))) {
            c.superminimo = valore;
            c.superminimoAssorbibile = false;
        } else if (e.startsWith("SUP") && e.contains("ASS")) {
            c.superminimo = valore;
            c.superminimoAssorbibile = true;
        } else if (e.startsWith("SUPERM") || e.startsWith("SUP.")) {
            c.superminimo = valore;
        } else if (e.startsWith("TOTALE")) {
            c.totaleRetribuzione = valore;
        } else {
            c.altriElementiFissi = Zone.piu(c.altriElementiFissi, valore);
        }
    }
}
