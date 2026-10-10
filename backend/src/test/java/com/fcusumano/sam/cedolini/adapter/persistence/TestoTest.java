package com.fcusumano.sam.cedolini.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestoTest {

    @Test
    void unElencoVuotoONullDiventaNull() {
        assertThat(Testo.elenco(List.of(), "\n", 100)).isNull();
        assertThat(Testo.elenco(Arrays.asList(" ", null), "\n", 100)).isNull();
    }

    @Test
    void gliACapoInterniDiventanoSpaziCosiOgniElementoRestaUnaRiga() {
        assertThat(Testo.elenco(List.of("primo\nsu due righe", "secondo"), "\n", 100))
                .isEqualTo("primo su due righe\nsecondo");
    }

    @Test
    void oltreIlLimiteLUltimaRigaETroncataEQuelleSuccessiveOmesse() {
        var elenco = Testo.elenco(List.of("aaaa", "bbbbbbbb", "cc"), "\n", 8);

        assertThat(elenco).isEqualTo("aaaa\nbbb").hasSizeLessThanOrEqualTo(8);
        assertThat(Testo.elenco(List.of("aaaaaaa", "b"), "\n", 8)).isEqualTo("aaaaaaa");
    }
}
