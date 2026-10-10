package com.fcusumano.sam.ccnl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestoNormalizzatoTest {

    @Test
    void riduceIlTestoAParoleMinuscoleSenzaAccentiEPunteggiatura() {
        assertThat(TestoNormalizzato.di("Metalmeccanici imp. (p.ind)").parole())
                .containsExactly("metalmeccanici", "imp", "p", "ind");
        assertThat(TestoNormalizzato.di("  Società  dell'Arte  ").parole())
                .containsExactly("societa", "dell", "arte");
        assertThat(TestoNormalizzato.di(null).vuoto()).isTrue();
    }

    @Test
    void trovaLeFrasiSoloComeSequenzeDiParoleIntere() {
        var testo = TestoNormalizzato.di("terziario distribuzione e servizi, distribuzione e servizi");
        assertThat(testo.occorrenze(TestoNormalizzato.di("Distribuzione e Servizi"))).containsExactly(1, 4);
        assertThat(testo.contiene(TestoNormalizzato.di("distribuzione servizi"))).isFalse();
        assertThat(testo.contiene(TestoNormalizzato.di("terz"))).isFalse();
    }
}
