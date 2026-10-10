/**
 * Modulo web: controller REST ({@code /api/v1}), DTO, gestione errori (Problem Details RFC 9457, con l'elenco
 * {@code errori} campo per campo per i dati non validi), sicurezza (permit-all in fase 1) + CORS per lo sviluppo,
 * documentazione OpenAPI (springdoc).
 * Nessuna logica di dominio: compone le API pubbliche degli altri moduli. Il contratto e' {@code docs/openapi.yaml}.
 * Nessun altro modulo dipende da web.
 */
@ApplicationModule(
        displayName = "Web API",
        allowedDependencies = {"shared", "ccnl", "anagrafica", "cedolini", "calcoli", "segnalazioni"})
package com.fcusumano.sam.web;

import org.springframework.modulith.ApplicationModule;
