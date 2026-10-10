package com.fcusumano.sam.cedolini.adapter.locale;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.config.AiProperties;
import com.fcusumano.sam.cedolini.config.LettoreProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Scelta del lettore con {@code salary-manager.lettore}: il lettore locale e' il predefinito, Claude e il lettore a
 * fixture si scelgono con la proprieta' (o con {@code SAM_LETTORE}). Nessuna chiamata di rete.
 */
class SceltaLettoreTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LettoreProperties.class, AiProperties.class})
    @ComponentScan(basePackages = {
            "com.fcusumano.sam.cedolini.adapter.locale",
            "com.fcusumano.sam.cedolini.adapter.ai",
            "com.fcusumano.sam.cedolini.adapter.fixture"})
    static class Lettori {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private final ApplicationContextRunner contesto = new ApplicationContextRunner().withUserConfiguration(Lettori.class);

    @Test
    void senzaConfigurazioneIlLettoreELocale() {
        contesto.run(c -> {
            assertThat(c).hasSingleBean(LettoreCedolini.class);
            assertThat(c.getBean(LettoreCedolini.class)).isInstanceOf(LettoreLocaleCedolini.class);
            assertThat(c.getBean(LettoreProperties.class).lettore()).isEqualTo(LettoreProperties.Lettore.LOCALE);
        });
    }

    @Test
    void ilLettoreLocaleSiPuoIndicareEsplicitamente() {
        contesto.withPropertyValues("salary-manager.lettore=locale").run(c ->
                assertThat(c.getBean(LettoreCedolini.class)).isInstanceOf(LettoreLocaleCedolini.class));
    }

    @Test
    void claudeSiSceglieConLaProprieta() {
        contesto.withPropertyValues("salary-manager.lettore=claude").run(c -> {
            assertThat(c).hasSingleBean(LettoreCedolini.class);
            assertThat(c.getBean(LettoreCedolini.class).getClass().getSimpleName()).isEqualTo("ClaudeLettoreCedolini");
        });
    }

    @Test
    void ilLettoreAFixtureSiSceglieConLaProprieta() {
        contesto.withPropertyValues("salary-manager.lettore=fixture").run(c -> {
            assertThat(c).hasSingleBean(LettoreCedolini.class);
            assertThat(c.getBean(LettoreCedolini.class).getClass().getSimpleName()).isEqualTo("FixtureLettoreCedolini");
        });
    }

    @Test
    void unValoreNonPrevistoBloccaLAvvio() {
        contesto.withPropertyValues("salary-manager.lettore=altro").run(c -> assertThat(c).hasFailed());
    }
}
