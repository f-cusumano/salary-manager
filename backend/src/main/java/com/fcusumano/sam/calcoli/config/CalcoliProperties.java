package com.fcusumano.sam.calcoli.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametri generali dei calcoli derivati ({@code salary-manager.calcoli.*}).
 *
 * @param tolleranzaAumento  variazione minima (euro) del lordo contrattuale o degli scatti considerata un aumento
 * @param mensilitaSenzaCcnl mensilita' assunte per la RAL quando il CCNL del rapporto non e' indicato (RAL stimata)
 * @param cedoliniBuoniPasto ultimi cedolini ordinari esaminati per "Buoni pasto: SI"
 */
@ConfigurationProperties("salary-manager.calcoli")
public record CalcoliProperties(
        @DefaultValue("0.01") BigDecimal tolleranzaAumento,
        @DefaultValue("13") int mensilitaSenzaCcnl,
        @DefaultValue("3") int cedoliniBuoniPasto) {
}
