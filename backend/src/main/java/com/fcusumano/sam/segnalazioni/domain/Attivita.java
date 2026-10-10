package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Utilita' comuni alle regole: giorni di attivita' (maturazione dei ratei), importi e testi stampati.
 */
final class Attivita {

    /** Giorni minimi di lavoro nel mese perche' maturi un rateo di tredicesima/quattordicesima. */
    static final int GIORNI_MINIMI_RATEO = 15;

    private Attivita() {
    }

    /** Giorni di calendario tra due date, estremi compresi (0 se {@code a} precede {@code da}). */
    static long giorni(LocalDate da, LocalDate a) {
        return a.isBefore(da) ? 0 : ChronoUnit.DAYS.between(da, a) + 1;
    }

    static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    static boolean positivo(BigDecimal importo) {
        return importo != null && importo.signum() > 0;
    }

    /** True se {@code nuovo} e' inferiore a {@code precedente} di oltre la tolleranza. Null = non confrontabile. */
    static boolean inCalo(BigDecimal precedente, BigDecimal nuovo, BigDecimal tolleranza) {
        return precedente != null && nuovo != null && precedente.subtract(nuovo).compareTo(tolleranza) > 0;
    }

    /** True se il cedolino liquida il TFR (importo lordo liquidato positivo). */
    static boolean tfrLiquidato(CedolinoNormalizzato cedolino) {
        return cedolino.tfr() != null && positivo(cedolino.tfr().liquidatoLordo());
    }

    /** Testo normalizzato per i confronti: maiuscolo, spazi compattati; null se vuoto. */
    static String normalizza(String testo) {
        if (testo == null || testo.isBlank()) {
            return null;
        }
        return testo.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** True se entrambi i testi sono presenti e diversi (es. CCNL stampato cambiato). */
    static boolean diversiEntrambiPresenti(String a, String b) {
        var na = normalizza(a);
        var nb = normalizza(b);
        return na != null && nb != null && !na.equals(nb);
    }

    /** Testo stampato abbreviato per le descrizioni. */
    static String abbrevia(String testo, int massimo) {
        var pulito = testo.trim().replaceAll("\\s+", " ");
        return pulito.length() <= massimo ? pulito : pulito.substring(0, massimo - 1) + "…";
    }
}
