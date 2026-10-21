package com.fcusumano.sam.web.api;

import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.ModificaCedolini;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.CedolinoDettaglioDto;
import com.fcusumano.sam.web.api.dto.CedolinoManualeDto;
import com.fcusumano.sam.web.api.dto.CedolinoModificatoDto;
import com.fcusumano.sam.web.api.dto.VerificaCedolinoManualeDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dettaglio, PDF originale, modifica a mano ed eliminazione dei cedolini.
 * <p>
 * La modifica usa la stessa forma dell'inserimento a mano ({@code CedolinoManuale}) e gli stessi errori campo per
 * campo; salvare sostituisce il cedolino (nuovo id) come se il suo PDF fosse caricato di nuovo con quei dati.
 */
@RestController
@RequestMapping(path = "/api/v1/cedolini/{cedolinoId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "cedolini", description = "Dettaglio, PDF, modifica ed eliminazione dei cedolini")
class CedoliniController {

    static final String CEDOLINO = "Cedolino";

    private final ArchivioCedolini archivio;
    private final ModificaCedolini modifica;

    CedoliniController(ArchivioCedolini archivio, ModificaCedolini modifica) {
        this.archivio = archivio;
        this.modifica = modifica;
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

    @GetMapping("/dati")
    @Operation(operationId = "datiCedolino", summary = "Dati del cedolino nella forma del modulo di modifica",
            description = "Gli stessi campi dell'inserimento a mano, dai dati da cui e' stato archiviato il cedolino, "
                    + "con persona e azienda del suo rapporto (personaId, aziendaId). 409 se i dati archiviati non "
                    + "sono piu' interpretabili.")
    @ApiResponse(responseCode = "200", description = "Dati del cedolino")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    CedolinoManualeDto datiCedolino(@PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId) {
        return Mappatura.cedolinoManuale(modifica.dati(new CedolinoId(cedolinoId)));
    }

    @PostMapping("/dati/verifica")
    @Operation(operationId = "verificaModificaCedolino",
            summary = "Prova la modifica dei dati senza salvare (competenza, conflitti, nuove anagrafiche, avvisi)",
            description = "Esegue la sostituzione del cedolino e la annulla: nessun dato viene salvato. 400 con errori "
                    + "(percorsi dello schema CedolinoManuale) come nell'inserimento a mano.")
    @ApiResponse(responseCode = "200", description = "Esito della prova")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    VerificaCedolinoManualeDto verificaModificaCedolino(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId,
            @RequestBody CedolinoManualeDto richiesta) {
        return Mappatura.verifica(modifica.verifica(new CedolinoId(cedolinoId), Mappatura.cedolinoManuale(richiesta)));
    }

    @PutMapping("/dati")
    @Operation(operationId = "modificaCedolino", summary = "Salva i dati modificati del cedolino",
            description = "Il cedolino e' sostituito come se il suo PDF fosse caricato di nuovo con questi dati: "
                    + "competenza, classificazione dei cedolini del rapporto e dati derivati vengono ricalcolati e il "
                    + "cedolino ha un nuovo id. Con persona o azienda diverse va a un altro rapporto (creato se manca); "
                    + "il rapporto precedente, se resta senza cedolini, e' eliminato con le anagrafiche orfane. 400 con "
                    + "errori come nella verifica; 409 se con questi dati sarebbe un duplicato o un aggiuntivo di un "
                    + "altro cedolino (nulla cambia).")
    @ApiResponse(responseCode = "200", description = "Cedolino modificato")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    CedolinoModificatoDto modificaCedolino(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long cedolinoId,
            @RequestBody CedolinoManualeDto richiesta) {
        return Mappatura.cedolinoModificato(modifica.modifica(new CedolinoId(cedolinoId),
                Mappatura.cedolinoManuale(richiesta)));
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
