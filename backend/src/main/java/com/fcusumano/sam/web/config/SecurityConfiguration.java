package com.fcusumano.sam.web.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Sicurezza predisposta per le fasi successive (login, workspace per utente). Fase 1: tutto permesso, nessuna sessione,
 * CSRF disattivato (API stateless consumate dalla SPA), CORS per il server di sviluppo del frontend.
 * <p>
 * Senza login la protezione e' la pubblicazione su localhost: il firewall ammette solo i nomi host configurati
 * ({@code salary-manager.web.allowed-hosts}), cosi' una pagina web il cui dominio viene risolto su 127.0.0.1 (DNS
 * rebinding, con Origin e Host coincidenti che CORS non ferma) non raggiunge le API. Le richieste rifiutate ricevono
 * 400 in Problem Details, senza dati della richiesta. Per lo stesso motivo il backend non parte se l'interfaccia di
 * ascolto ({@code server.address}) e' vuota.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    /** Corpo della risposta alle richieste rifiutate dal firewall. */
    static final String PROBLEMA_RICHIESTA_RIFIUTATA = "{\"type\":\"https://salary-manager.local/problemi/"
            + "richiesta-non-valida\",\"title\":\"Richiesta non valida\",\"status\":400,"
            + "\"detail\":\"Richiesta rifiutata: indirizzo o nome host non ammesso.\"}";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    WebSecurityCustomizer firewallNomiHost(WebProperties properties) {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowedHostnames(properties::hostAmmesso);
        RequestRejectedHandler rifiuto = (request, response, eccezione) -> {
            // solo il tipo: il messaggio dell'eccezione riporta parti della richiesta
            log.warn("Richiesta rifiutata dal firewall: {}", eccezione.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(PROBLEMA_RICHIESTA_RIFIUTATA);
        };
        return web -> web.httpFirewall(firewall).requestRejectedHandler(rifiuto);
    }

    /**
     * Blocca l'avvio se {@code server.address} e' vuoto (per esempio {@code SAM_SERVER_ADDRESS} impostata vuota
     * nell'ambiente): il server ascolterebbe su tutte le interfacce invece che sul default 127.0.0.1.
     */
    @Bean
    WebServerFactoryCustomizer<ConfigurableWebServerFactory> indirizzoDiAscoltoNonVuoto(Environment ambiente) {
        return factory -> {
            var indirizzo = ambiente.getProperty("server.address");
            if (indirizzo != null && indirizzo.isBlank()) {
                throw new IllegalStateException("server.address (SAM_SERVER_ADDRESS) e' vuoto: il backend "
                        + "ascolterebbe su tutte le interfacce. Togli la variabile per il default 127.0.0.1, oppure "
                        + "indica 0.0.0.0 per ascoltare davvero su tutte");
            }
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("Content-Disposition", "Location"));
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
