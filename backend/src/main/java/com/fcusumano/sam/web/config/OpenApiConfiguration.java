package com.fcusumano.sam.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import java.time.YearMonth;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadati della documentazione OpenAPI generata da springdoc ({@code /v3/api-docs}, UI su {@code /swagger-ui.html}).
 * Il contratto di riferimento per il frontend e' {@code docs/openapi.yaml}: le due descrizioni devono restare allineate
 * (verificato da {@code ContrattoOpenApiTest}).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    /** Formato dei mesi nel JSON ("2024-01"), come lo schema {@code Mese} del contratto. */
    static final String PATTERN_MESE = "^\\d{4}-(0[1-9]|1[0-2])$";

    static {
        SpringDocUtils.getConfig().replaceWithSchema(YearMonth.class,
                new StringSchema().pattern(PATTERN_MESE).description("Mese nel formato YYYY-MM"));
    }

    @Bean
    OpenAPI salaryManagerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Salary Manager API")
                .version("v1")
                .description("API REST di Salary Manager: persone, rapporti di lavoro, cedolini, riepiloghi e "
                        + "segnalazioni. Contratto di riferimento: docs/openapi.yaml."));
    }
}
