package com.fcusumano.sam.web.api;

import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Periodi;

/**
 * Titoli pronti per la UI delle mensilita' e dei cedolini.
 */
final class Titoli {

    private Titoli() {
    }

    /**
     * "Gennaio 2024", "Tredicesima 2024", "Quattordicesima 2024", "Fine rapporto 1 – Aprile 2020"
     * (progressivo e periodo di retribuzione).
     */
    static String di(Competenza competenza) {
        return switch (competenza) {
            case Competenza.Mensilita m when m.mese() == 13 -> "Tredicesima " + m.anno();
            case Competenza.Mensilita m when m.mese() == 14 -> "Quattordicesima " + m.anno();
            case Competenza.Mensilita m -> Periodi.descrizione(m.comeYearMonth());
            case Competenza.FineRapporto f ->
                    "Fine rapporto " + f.progressivo() + " – " + Periodi.descrizione(f.periodoRetribuzione());
        };
    }

    /** Chiave univoca nel rapporto: "2024.01", "2024.13", "2020.TFR01". */
    static String chiave(Competenza competenza) {
        return competenza.anno() + "." + competenza.codice();
    }
}
