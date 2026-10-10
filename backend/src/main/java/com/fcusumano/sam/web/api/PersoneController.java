package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.web.api.dto.PersonaSintesiDto;
import com.fcusumano.sam.web.api.dto.RapportoSintesiDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persone (tendina della barra superiore e scelta della persona nell'inserimento a mano) e loro rapporti di lavoro
 * (elenco aziende).
 */
@RestController
@RequestMapping(path = "/api/v1/persone", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "persone", description = "Persone e loro rapporti di lavoro")
class PersoneController {

    private final ConsultazioneAnagrafica anagrafica;
    private final ComposizioneRapporti rapporti;

    PersoneController(ConsultazioneAnagrafica anagrafica, ComposizioneRapporti rapporti) {
        this.anagrafica = anagrafica;
        this.rapporti = rapporti;
    }

    @GetMapping
    @Operation(operationId = "elencoPersone", summary = "Elenco persone per la tendina",
            description = "Persone del workspace ordinate per cognome e nome. Lista vuota prima dei caricamenti. "
                    + "Usata anche dall'inserimento a mano per scegliere una persona gia' in archivio.")
    @ApiResponse(responseCode = "200", description = "Elenco persone")
    List<PersonaSintesiDto> elencoPersone() {
        return anagrafica.elencoPersone().stream().map(Mappatura::persona).toList();
    }

    @GetMapping("/{personaId}/rapporti")
    @Operation(operationId = "rapportiDiPersona", summary = "Aziende (rapporti di lavoro) di una persona",
            description = "Rapporti in ordine cronologico inverso (il rapporto attuale per primo).")
    @ApiResponse(responseCode = "200", description = "Rapporti della persona")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    List<RapportoSintesiDto> rapportiDiPersona(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long personaId) {
        return rapporti.rapportiDiPersona(personaId);
    }
}
