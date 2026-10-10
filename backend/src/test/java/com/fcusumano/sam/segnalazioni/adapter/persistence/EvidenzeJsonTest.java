package com.fcusumano.sam.segnalazioni.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.shared.Competenza;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenzeJsonTest {

    @Test
    void andataERitornoConservaTuttiICampi() {
        var evidenze = List.of(
                new Evidenza("Fondo TFR al 31/12/2023 (Gennaio 2024)", new CedolinoId(12),
                        new Competenza.Mensilita(2024, 1), "4.526,32 €"),
                new Evidenza("Cedolino finale: Fine rapporto 1 – Luglio 2024", new CedolinoId(13),
                        new Competenza.FineRapporto(1, YearMonth.of(2024, 7)), null),
                new Evidenza("Data di cessazione", null, null, "15 Giugno 2024"));

        var json = EvidenzeJson.scrivi(evidenze);

        assertThat(json).contains("\"tipo\":\"MENSILITA\"", "\"tipo\":\"FINE_RAPPORTO\"", "\"periodo\":\"2024-07\"");
        assertThat(EvidenzeJson.leggi(json)).isEqualTo(evidenze);
    }

    @Test
    void jsonAssenteOVuoto() {
        assertThat(EvidenzeJson.leggi(null)).isEmpty();
        assertThat(EvidenzeJson.leggi(" ")).isEmpty();
        assertThat(EvidenzeJson.leggi(EvidenzeJson.scrivi(List.of()))).isEmpty();
    }
}
