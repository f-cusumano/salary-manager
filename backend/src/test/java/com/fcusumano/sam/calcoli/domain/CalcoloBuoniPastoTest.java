package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloBuoniPastoTest {

    private static boolean buoniPasto(ParametriCalcolo parametri, CedolinoDiProva... cedolini) {
        return CalcoloBuoniPasto.calcola(
                CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList()), parametri);
    }

    @Test
    void siSePresentiInUnoDegliUltimiTreOrdinari() {
        assertThat(buoniPasto(PARAMETRI, ordinario(2024, 1), ordinario(2024, 2).buoniPasto(), ordinario(2024, 3),
                ordinario(2024, 4))).isTrue();
    }

    @Test
    void noSePresentiSoloPrimaDegliUltimiTre() {
        assertThat(buoniPasto(PARAMETRI, ordinario(2024, 1).buoniPasto(), ordinario(2024, 2), ordinario(2024, 3),
                ordinario(2024, 4))).isFalse();
    }

    @Test
    void riconosciutiAncheDallaDescrizioneDellaVoce() {
        assertThat(buoniPasto(PARAMETRI,
                ordinario(2024, 1).voce("Ticket Restaurant elettronici", CategoriaVoce.NON_RETRIBUTIVA, "160.00")))
                .isTrue();
        assertThat(buoniPasto(PARAMETRI,
                ordinario(2024, 1).voce("Buono pasto", CategoriaVoce.INFORMATIVA, "8.00"))).isTrue();
        assertThat(buoniPasto(PARAMETRI,
                ordinario(2024, 1).voce("Rimborso chilometrico", CategoriaVoce.NON_RETRIBUTIVA, "80.00"))).isFalse();
    }

    @Test
    void cedoliniNonOrdinariNonContanoEdNonOccupanoPosti() {
        // La tredicesima con buoni non conta; dicembre (con buoni) resta tra gli ultimi tre ordinari
        assertThat(buoniPasto(PARAMETRI, ordinario(2024, 10), ordinario(2024, 11), ordinario(2024, 12).buoniPasto(),
                tredicesima(2024))).isTrue();
        assertThat(buoniPasto(PARAMETRI, ordinario(2024, 10), ordinario(2024, 11), ordinario(2024, 12),
                tredicesima(2024).buoniPasto())).isFalse();
    }

    @Test
    void numeroDiCedoliniConfigurabile() {
        var soloUltimo = new ParametriCalcolo(new BigDecimal("26"), new BigDecimal("173"), new BigDecimal("8"),
                new BigDecimal("13.5"), new BigDecimal("0.23"), new BigDecimal("0.17"), new BigDecimal("0.01"), 13, 1,
                new BigDecimal("0.03"), 12);

        assertThat(buoniPasto(soloUltimo, ordinario(2024, 1).buoniPasto(), ordinario(2024, 2))).isFalse();
        assertThat(buoniPasto(soloUltimo, ordinario(2024, 1), ordinario(2024, 2).buoniPasto())).isTrue();
    }

    @Test
    void senzaCedoliniNo() {
        assertThat(buoniPasto(PARAMETRI)).isFalse();
    }
}
