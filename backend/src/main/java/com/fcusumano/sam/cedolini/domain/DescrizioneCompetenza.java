package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Periodi;

/**
 * Descrizione leggibile di una competenza, usata nei messaggi dei caricamenti (mai dati personali):
 * "Gennaio 2024", "Tredicesima 2023", "Quattordicesima 2024", "Fine rapporto di Luglio 2024".
 */
public final class DescrizioneCompetenza {

    private DescrizioneCompetenza() {
    }

    public static String di(Competenza competenza) {
        return switch (competenza) {
            case Competenza.Mensilita m when m.mese() == 13 -> "Tredicesima " + m.anno();
            case Competenza.Mensilita m when m.mese() == 14 -> "Quattordicesima " + m.anno();
            case Competenza.Mensilita m -> Periodi.descrizione(m.comeYearMonth());
            case Competenza.FineRapporto f -> "Fine rapporto di " + Periodi.descrizione(f.periodoRetribuzione());
        };
    }
}
