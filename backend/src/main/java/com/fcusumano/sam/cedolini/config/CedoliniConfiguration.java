package com.fcusumano.sam.cedolini.config;

import com.fcusumano.sam.cedolini.domain.NomeFileCedolinoStrategy;
import com.fcusumano.sam.cedolini.domain.PatternNomeFileCedolino;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean del modulo cedolini che collegano il dominio (Java puro) a Spring.
 */
@Configuration(proxyBeanMethods = false)
class CedoliniConfiguration {

    @Bean
    @ConditionalOnMissingBean
    NomeFileCedolinoStrategy nomeFileCedolinoStrategy(StorageProperties storage) {
        return new PatternNomeFileCedolino(storage.nomeFilePattern());
    }
}
