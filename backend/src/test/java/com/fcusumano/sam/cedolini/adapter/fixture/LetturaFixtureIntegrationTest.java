package com.fcusumano.sam.cedolini.adapter.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contesto Spring del profilo test: lettoreCedolini fixture e lettore PDF collegati come li usera' il caricamento
 * (decifratura del PDF protetto, poi estrazione della copia in chiaro). Dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
class LetturaFixtureIntegrationTest {

    @Autowired
    LettoreCedolini lettoreCedolini;

    @Autowired
    LettorePdf lettorePdf;

    private static byte[] pdf(String nome) throws IOException {
        try (InputStream in = LetturaFixtureIntegrationTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            return in.readAllBytes();
        }
    }

    @Test
    void ilProfiloTestUsaIlLettoreAFixture() {
        assertThat(lettoreCedolini).isInstanceOf(FixtureLettoreCedolini.class);
    }

    @Test
    void unPdfProtettoSiDecifraConIlCodiceFiscaleESiEstrae() throws IOException {
        var originale = pdf("rossi-beta-2024-09");

        assertThatThrownBy(() -> lettorePdf.apri(originale, null, List::of))
                .isInstanceOf(PdfNonLeggibileException.class)
                .hasMessage("PDF protetto da password: indica la password e ricarica");

        var documento = lettorePdf.apri(originale, null, () -> List.of("RSSMRA80A01H501U"));
        var risultato = lettoreCedolini.estrai(documento.contenutoPerEstrazione(), "2024-09.pdf");

        assertThat(documento.protetto()).isTrue();
        assertThat(risultato.cedolino().periodo().anno()).isEqualTo(2024);
        assertThat(risultato.cedolino().periodo().mese()).isEqualTo(9);
        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(risultato.cedolino().buoniPasto().presenti()).isTrue();
    }

    @Test
    void unPdfNonProtettoPassaInvariatoAlLettore() throws IOException {
        var originale = pdf("rossi-alfa-2023-03");

        var documento = lettorePdf.apri(originale, "", List::of);
        var risultato = lettoreCedolini.estrai(documento.contenutoPerEstrazione());

        assertThat(documento.contenutoPerEstrazione()).isSameAs(originale);
        assertThat(risultato.cedolino().voci()).anyMatch(v -> v.descrizione().equals("Malattia"));
    }
}
