package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatiContrattualiCorrentiTest {

    @Test
    void ogniDatoVieneDalCedolinoPiuRecenteCheLoRiporta() {
        var maggio = cedolino(new Competenza.Mensilita(2024, 5), LocalDate.of(2021, 9, 1), null, "Impiegato", "5",
                "CCNL Metalmeccanica Industria");
        var giugno = cedolino(new Competenza.Mensilita(2024, 6), null, LocalDate.of(2024, 6, 15), null, "6", null);
        var tredicesima = cedolino(Competenza.Mensilita.tredicesima(2023), LocalDate.of(2020, 1, 1), null, "Operaio",
                "4", "Altro");

        var dati = DatiContrattualiCorrenti.da(List.of(tredicesima, giugno, maggio));

        assertThat(dati).isEqualTo(new DatiContrattualiCorrenti(LocalDate.of(2021, 9, 1), LocalDate.of(2024, 6, 15),
                "Impiegato", "6", null, "CCNL Metalmeccanica Industria", YearMonth.of(2024, 6)));
    }

    @Test
    void senzaCedoliniNessunDato() {
        assertThat(DatiContrattualiCorrenti.da(List.of())).isNull();
    }

    private static CedolinoNormalizzato cedolino(Competenza.Mensilita competenza, LocalDate assunzione,
                                                 LocalDate cessazione, String qualifica, String livello, String ccnl) {
        var periodo = YearMonth.of(competenza.anno(), Math.min(competenza.mese(), 12));
        return new CedolinoNormalizzato(null, new RapportoLavoroId(1), null, null, competenza, periodo, null, null,
                assunzione, cessazione, qualifica, livello, null, ccnl, null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, false, null);
    }
}
