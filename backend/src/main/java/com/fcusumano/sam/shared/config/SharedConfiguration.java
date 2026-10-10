package com.fcusumano.sam.shared.config;

import java.time.Clock;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione condivisa: orologio di sistema (sostituibile nei test) e impostazioni jOOQ.
 */
@Configuration(proxyBeanMethods = false)
class SharedConfiguration {

    /** Orologio usato per "oggi" nei calcoli: nei test si sostituisce con un Clock fisso. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * I nomi generati dal codegen (maiuscoli, H2) vengono resi senza virgolette: cosi' le stesse query funzionano su
     * H2 (che porta in maiuscolo i nomi non quotati) e su PostgreSQL (che li porta in minuscolo).
     */
    @Bean
    DefaultConfigurationCustomizer sharedJooqCustomizer() {
        return configuration -> configuration.settings()
                .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED);
    }
}
