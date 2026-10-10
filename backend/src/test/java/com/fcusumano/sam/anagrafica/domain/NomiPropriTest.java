package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomiPropriTest {

    @Test
    void convertiInInizialiMaiuscoleITestiTuttiMaiuscoliOMinuscoli() {
        assertThat(NomiPropri.normalizza("MARIO")).isEqualTo("Mario");
        assertThat(NomiPropri.normalizza("  DE   LUCA ")).isEqualTo("De Luca");
        assertThat(NomiPropri.normalizza("D'ANGELO")).isEqualTo("D'Angelo");
        assertThat(NomiPropri.normalizza("anna-maria")).isEqualTo("Anna-Maria");
    }

    @Test
    void lasciaInvariatiITestiGiaInFormaMista() {
        assertThat(NomiPropri.normalizza("McDonald")).isEqualTo("McDonald");
        assertThat(NomiPropri.normalizza(" ")).isNull();
    }
}
