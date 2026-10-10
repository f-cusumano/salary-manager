package com.fcusumano.sam.web.api;

import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.CedolinoDettaglioDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dettaglio, PDF originale ed eliminazione dei cedolini.
 */
@RestController
@RequestMapping(path = "/api/v1/cedolini/{cedolinoId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "cedolini", description = "Dettaglio, PDF ed eliminazione dei cedolini")
class CedoliniController {

    static final String CEDOLINO = "Cedolino";

    private final ArchivioCedolini archivio;

    CedoliniController(ArchivioCedolini archivio) {
        this.archivio = archivio;
    }

    @GetMapping
    @Operation(operationId = "dettaglioCedolino", summary = "Dettaglio di un cedolino per aree tematiche")
    @ApiResponse(responseCode = "200", description = "Dettaglio del cedolino")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    CedolinoDettaglioDto dettaglioCedolino(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId) {
        return archivio.dettaglio(new CedolinoId(cedolinoId))
                .map(Mappatura::cedolino)
                .orElseThrow(() -> new RisorsaNonTrovataException(CEDOLINO, cedolinoId));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "eliminaCedolino", summary = "Elimina un cedolino",
            description = "Elimina dati e PDF; i dati derivati del rapporto vengono ricalcolati da zero. Se era "
                    + "l'ultimo cedolino del rapporto, vengono eliminati anche il rapporto e l'eventuale "
                    + "persona/azienda rimaste senza rapporti.")
    @ApiResponse(responseCode = "204", description = "Cedolino eliminato")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    void eliminaCedolino(@PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId) {
        archivio.elimina(new CedolinoId(cedolinoId));
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "scaricaPdfCedolino", summary = "Scarica il PDF originale",
            description = "Il nome del file e' quello normalizzato (es. 2024.01_3_1.pdf), nell'header "
                    + "Content-Disposition (filename e filename* RFC 5987).")
    @ApiResponse(responseCode = "200", description = "PDF originale",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                    description = "attachment; filename=\"2024.01_3_1.pdf\"; filename*=UTF-8''2024.01_3_1.pdf",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    ResponseEntity<byte[]> scaricaPdfCedolino(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId) {
        var pdf = archivio.pdf(new CedolinoId(cedolinoId))
                .orElseThrow(() -> new RisorsaNonTrovataException(CEDOLINO, cedolinoId));
        var disposizione = ContentDisposition.attachment().filename(pdf.nomeFile(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.contenuto().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposizione.toString())
                .body(pdf.contenuto());
    }
}
