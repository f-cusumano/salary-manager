package com.fcusumano.sam.web.api;

import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.web.api.dto.CcnlDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tabella CCNL per la scelta del contratto del rapporto. Senza parametri: e' anche l'healthcheck del container.
 */
@RestController
@RequestMapping(path = "/api/v1/ccnl", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "ccnl", description = "Tabella CCNL")
class CcnlController {

    private final CatalogoCcnl catalogo;

    CcnlController(CatalogoCcnl catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    @Operation(operationId = "elencoCcnl", summary = "Elenco CCNL selezionabili",
            description = "Senza parametri; usato anche come healthcheck del container backend.")
    @ApiResponse(responseCode = "200", description = "CCNL ordinati per descrizione")
    List<CcnlDto> elencoCcnl() {
        return catalogo.elenco().stream().map(Mappatura::ccnl).toList();
    }
}
