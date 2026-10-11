package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.web.api.dto.AziendaSelezionabileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aziende (datori di lavoro) da scegliere nell'inserimento a mano, invece di riscriverne i dati.
 */
@RestController
@RequestMapping(path = "/api/v1/aziende", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "aziende", description = "Aziende (datori di lavoro)")
class AziendeController {

    private final ConsultazioneAnagrafica anagrafica;

    AziendeController(ConsultazioneAnagrafica anagrafica) {
        this.anagrafica = anagrafica;
    }

    @GetMapping
    @Operation(operationId = "elencoAziende", summary = "Elenco aziende per la scelta del datore di lavoro",
            description = "Aziende del workspace ordinate per ragione sociale (senza distinzione tra maiuscole e "
                    + "minuscole), poi per id, con l'etichetta anti-omonimia calcolata su tutte le aziende del "
                    + "workspace. L'inserimento a mano la usa per scegliere un'azienda gia' in archivio invece di "
                    + "riscriverne i dati. Lista vuota prima dei caricamenti.")
    @ApiResponse(responseCode = "200", description = "Elenco aziende")
    List<AziendaSelezionabileDto> elencoAziende() {
        return anagrafica.elencoAziende().stream().map(Mappatura::aziendaSelezionabile).toList();
    }
}
