package com.fcusumano.sam.cedolini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Lettore dei cedolini ({@code salary-manager.lettore}, variabile {@code SAM_LETTORE}) e parametri comuni a tutti i
 * lettori. Un valore non previsto blocca l'avvio con l'elenco dei valori ammessi.
 *
 * @param lettore              LOCALE (predefinito: gratuito e offline, layout noti con testo selezionabile), CLAUDE
 *                             (API Anthropic, a pagamento: anche scansioni e layout nuovi; impostazioni in
 *                             {@code salary-manager.ai.*}) o FIXTURE (JSON di fixture, per test e modalita' demo)
 * @param lettureContemporanee letture contemporanee massime dei cedolini caricati
 * @param fixtureDir           cartella dei JSON del lettore FIXTURE, es. "classpath:fixtures/cedolini/" (i PDF di prova
 *                             sono nella cartella sorella {@code pdf/})
 */
@ConfigurationProperties("salary-manager")
public record LettoreProperties(
        @DefaultValue("locale") Lettore lettore,
        @DefaultValue("2") int lettureContemporanee,
        @DefaultValue("classpath:fixtures/cedolini/") String fixtureDir) {

    /** Valori di {@code salary-manager.lettore} (senza distinzione tra maiuscole e minuscole). */
    public enum Lettore {
        LOCALE,
        CLAUDE,
        FIXTURE
    }
}
