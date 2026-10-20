package com.fcusumano.sam.calcoli.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametri della stima della liquidazione ({@code salary-manager.liquidazione.*}).
 *
 * @param aliquotaContributiDefault aliquota dei contributi INPS a carico del dipendente se il cedolino non la stampa
 * @param irpef                     scaglioni IRPEF per anno di entrata in vigore
 */
@ConfigurationProperties("salary-manager.liquidazione")
public record LiquidazioneProperties(
        @DefaultValue("0.0919") BigDecimal aliquotaContributiDefault,
        List<Irpef> irpef) {

    /**
     * @param dalAnno   primo anno di applicazione
     * @param scaglioni in ordine crescente; l'ultimo senza {@code fino}
     */
    public record Irpef(int dalAnno, List<Scaglione> scaglioni) {
    }

    /**
     * @param fino     limite superiore dello scaglione (incluso); assente per l'ultimo
     * @param aliquota aliquota tra 0 e 1
     */
    public record Scaglione(BigDecimal fino, BigDecimal aliquota) {
    }
}
