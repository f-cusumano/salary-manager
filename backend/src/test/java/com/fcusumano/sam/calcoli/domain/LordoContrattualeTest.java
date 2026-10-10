package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.euro;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.domain.LordoContrattuale.Origine;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import org.junit.jupiter.api.Test;

class LordoContrattualeTest {

    @Test
    void totaleStampatoPrevale() {
        var e = new ElementiRetributivi(euro("2000"), null, euro("300"), true, euro("25"), null, null, euro("2330"),
                null, null, null, null);

        assertThat(LordoContrattuale.di(e)).get().satisfies(l -> {
            assertThat(l.importo()).isEqualByComparingTo("2330");
            assertThat(l.origine()).isEqualTo(Origine.TOTALE_STAMPATO);
        });
    }

    @Test
    void sommaDegliElementiConMinimo() {
        var e = new ElementiRetributivi(euro("2000"), euro("500"), euro("300"), true, euro("25"), euro("10"), null,
                null, null, null, null, null);

        assertThat(LordoContrattuale.di(e)).get().satisfies(l -> {
            assertThat(l.importo()).isEqualByComparingTo("2835");
            assertThat(l.origine()).isEqualTo(Origine.SOMMA_ELEMENTI);
        });
    }

    @Test
    void retribuzioneBaseConSuperminimoESomma() {
        var e = new ElementiRetributivi(null, null, euro("150"), false, null, null, euro("2000"), null,
                null, null, null, null);

        assertThat(LordoContrattuale.di(e)).get().satisfies(l -> {
            assertThat(l.importo()).isEqualByComparingTo("2150");
            assertThat(l.origine()).isEqualTo(Origine.SOMMA_ELEMENTI);
        });
    }

    @Test
    void soloRetribuzioneBase() {
        var e = new ElementiRetributivi(null, null, null, null, null, null, euro("2600"), null,
                null, null, null, null);

        assertThat(LordoContrattuale.di(e)).get().satisfies(l -> {
            assertThat(l.importo()).isEqualByComparingTo("2600");
            assertThat(l.origine()).isEqualTo(Origine.RETRIBUZIONE_BASE);
        });
    }

    @Test
    void soloSuperminimoOTotaleNulloNonBastano() {
        var soloSuperminimo = new ElementiRetributivi(null, null, euro("300"), true, null, null, null, null,
                null, null, null, null);
        var totaleZero = new ElementiRetributivi(null, null, null, null, null, null, null, euro("0"),
                euro("15"), euro("100"), null, null);

        assertThat(LordoContrattuale.di(soloSuperminimo)).isEmpty();
        assertThat(LordoContrattuale.di(totaleZero)).isEmpty();
        assertThat(LordoContrattuale.di(ElementiRetributivi.VUOTI)).isEmpty();
    }
}
