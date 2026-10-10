package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.domain.ConfrontoCedolini.ImportiChiave;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConfrontoCedoliniTest {

    private static final ImportiChiave GENNAIO = new ImportiChiave(new BigDecimal("1652.00"),
            new BigDecimal("2325.00"), new BigDecimal("2325.00"), new BigDecimal("673.20"));

    @Test
    void stessiImportiConScalaDiversaSonoLoStessoContenuto() {
        var ristampa = new ImportiChiave(new BigDecimal("1652.00000"), new BigDecimal("2325"),
                new BigDecimal("2325.000"), new BigDecimal("673.2"));
        assertThat(ConfrontoCedolini.stessoContenuto(GENNAIO, ristampa)).isTrue();
    }

    @Test
    void unImportoDiversoRendeIlCedolinoUnAggiuntivo() {
        var aggiuntivo = new ImportiChiave(new BigDecimal("690.16"), GENNAIO.lordo(), GENNAIO.totaleCompetenze(),
                GENNAIO.totaleTrattenute());
        assertThat(ConfrontoCedolini.stessoContenuto(GENNAIO, aggiuntivo)).isFalse();
    }

    @Test
    void unImportoPresenteSoloSuUnoDeiDueNonCoincide() {
        var senzaLordo = new ImportiChiave(GENNAIO.netto(), null, GENNAIO.totaleCompetenze(), GENNAIO.totaleTrattenute());
        assertThat(ConfrontoCedolini.stessoContenuto(GENNAIO, senzaLordo)).isFalse();
    }
}
