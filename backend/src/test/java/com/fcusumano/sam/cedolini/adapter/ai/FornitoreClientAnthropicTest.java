package com.fcusumano.sam.cedolini.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.config.AiProperties;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifica della chiave API prima della creazione del client: nessuna chiamata all'API, solo chiavi inventate.
 */
class FornitoreClientAnthropicTest {

    private static final AiProperties PROPERTIES = new AiProperties("claude-opus-5-5", "medium", 16000,
            Duration.ofMinutes(5), 1, 2, true);

    @Test
    void gliSpaziAiLatiDellaChiaveVengonoTolti() {
        assertThat(FornitoreClientAnthropic.chiaveValida("  sk-ant-FINTA-chiave_01\t\r\n"))
                .isEqualTo("sk-ant-FINTA-chiave_01");
    }

    @Test
    void sonoAmmessiTuttiICaratteriAsciiStampabili() {
        var chiave = "sk-ant-!\"#$%&'()*+,-./09:;<=>?@AZ[\\]^_`az{|}~";
        var fornitore = new FornitoreClientAnthropic(PROPERTIES, variabile -> chiave);
        try {
            assertThat(fornitore.get()).isNotNull();
        } finally {
            fornitore.close();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\r\n"})
    void unaChiaveAssenteOVuotaEUnErroreDiConfigurazione(String valore) {
        var fornitore = new FornitoreClientAnthropic(PROPERTIES, variabile -> valore);

        assertThatThrownBy(fornitore::get)
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.CONFIGURAZIONE);
                    assertThat(e.getMessage()).startsWith("Chiave API Anthropic assente").contains("ANTHROPIC_API_KEY");
                });
    }

    static Stream<Arguments> chiaviNonValide() {
        return Stream.of(
                Arguments.of("sk-ant-FINTA SEGRETA-123", 13, "uno spazio"),
                Arguments.of("sk-ant-FINTA-SEGRETA-123 ", 25, "un carattere non ASCII"),
                Arguments.of("“sk-ant-FINTA-SEGRETA-123”", 1, "un carattere non ASCII"),
                Arguments.of("sk-ant-FINTA-SEGRETÀ-123", 20, "un carattere non ASCII"),
                Arguments.of("sk-ant-FINTA\u0007SEGRETA-123", 13, "un carattere di controllo"),
                Arguments.of("sk-ant-FINTA-SEGRETA-123\u007f", 25, "un carattere di controllo"));
    }

    @ParameterizedTest
    @MethodSource("chiaviNonValide")
    void unaChiaveConCaratteriNonAmmessiNonCreaIlClientENonVieneRipetuta(String chiave, int posizione, String tipo) {
        var fornitore = new FornitoreClientAnthropic(PROPERTIES, variabile -> chiave);

        assertThatThrownBy(fornitore::get)
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.CONFIGURAZIONE);
                    assertThat(e.getMessage())
                            .startsWith("Chiave API Anthropic non valida: ANTHROPIC_API_KEY contiene " + tipo)
                            .contains("(carattere n. " + posizione + ")")
                            .doesNotContain("FINTA", "SEGRET", "123");
                    assertThat(e.getCause()).isNull();
                });
    }
}
