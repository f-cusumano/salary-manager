package com.fcusumano.sam.cedolini.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurazione del lettore Claude ({@code salary-manager.ai.*}), usata solo con {@code salary-manager.lettore=claude}
 * (la scelta del lettore e' in {@link LettoreProperties}). La chiave API NON e' una property: si legge solo da
 * {@code ANTHROPIC_API_KEY}, variabile d'ambiente o file {@code .env} della radice del progetto
 * ({@code shared.config.VariabiliAmbiente}).
 *
 * @param model           modello Claude
 * @param effort          livello di effort (low, medium, high, xhigh, max)
 * @param maxTokens       massimo token in output per cedolino (thinking compreso; oltre 21333 si usa lo streaming)
 * @param timeout         timeout della singola richiesta
 * @param concurrency     richieste contemporanee massime all'API
 * @param maxRetries      tentativi automatici dell'SDK su errori transitori (429, 5xx, rete)
 * @param fallbackRifiuti fallback lato server sui rifiuti: se il modello rifiuta per una categoria di sicurezza, l'API
 *                        ripete la richiesta sul modello di fallback raccomandato
 */
@ConfigurationProperties("salary-manager.ai")
public record AiProperties(
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("medium") String effort,
        @DefaultValue("32000") int maxTokens,
        @DefaultValue("5m") Duration timeout,
        @DefaultValue("2") int concurrency,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("true") boolean fallbackRifiuti) {
}
