package com.fcusumano.sam.web.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurazione del modulo web ({@code salary-manager.web.*}).
 *
 * @param corsAllowedOrigins origini ammesse per CORS (sviluppo: server Vite su localhost:5173)
 * @param allowedHosts       nomi host ammessi nell'header Host (senza porta, senza distinzione tra maiuscole e
 *                           minuscole): le richieste per altri nomi sono rifiutate, contro il DNS rebinding verso
 *                           un'applicazione senza login pubblicata su localhost
 */
@ConfigurationProperties("salary-manager.web")
public record WebProperties(
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
        @DefaultValue({"localhost", "127.0.0.1", "[::1]", "::1"}) List<String> allowedHosts) {

    public WebProperties {
        corsAllowedOrigins = List.copyOf(corsAllowedOrigins);
        allowedHosts = allowedHosts.stream()
                .map(String::strip)
                .filter(h -> !h.isEmpty())
                .map(h -> h.toLowerCase(Locale.ROOT))
                .toList();
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("salary-manager.web.allowed-hosts deve indicare almeno un nome host");
        }
    }

    /** True se il nome host della richiesta (senza porta) e' tra quelli ammessi. */
    public boolean hostAmmesso(String nomeHost) {
        return nomeHost != null && allowedHosts.contains(nomeHost.toLowerCase(Locale.ROOT));
    }
}
