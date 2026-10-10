package com.fcusumano.sam.calcoli.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametri per la stima del valore di ferie e permessi ({@code salary-manager.ferie.*}), usati solo quando il
 * cedolino non riporta paga giornaliera/oraria o divisori.
 *
 * @param divisoreGiornaliero divisore per ricavare la paga giornaliera dal lordo mensile (es. 26)
 * @param divisoreOrario      divisore per ricavare la paga oraria dal lordo mensile (es. 173)
 * @param oreGiornaliere      ore per giornata, per convertire tra giorni e ore se serve
 */
@ConfigurationProperties("salary-manager.ferie")
public record FerieProperties(
        @DefaultValue("26") BigDecimal divisoreGiornaliero,
        @DefaultValue("173") BigDecimal divisoreOrario,
        @DefaultValue("8") BigDecimal oreGiornaliere) {
}
