package com.fcusumano.sam.cedolini.config;

import com.fcusumano.sam.cedolini.domain.PatternNomeFileCedolino;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Archiviazione dei PDF ({@code salary-manager.storage.*}).
 *
 * @param nomeFilePattern pattern del nome file normalizzato (vedi {@link PatternNomeFileCedolino})
 */
@ConfigurationProperties("salary-manager.storage")
public record StorageProperties(
        @DefaultValue(PatternNomeFileCedolino.PATTERN_DEFAULT) String nomeFilePattern) {
}
