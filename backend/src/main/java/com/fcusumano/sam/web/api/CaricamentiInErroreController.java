package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.CaricamentoDto;
import com.fcusumano.sam.web.api.dto.CaricamentoInErroreDto;
import com.fcusumano.sam.web.api.dto.CedolinoManualeDto;
import com.fcusumano.sam.web.api.dto.LottoCaricamentoDto;
import com.fcusumano.sam.web.api.dto.RichiestaAttribuzioneDto;
import com.fcusumano.sam.web.api.dto.RichiestaRiletturaDto;
import com.fcusumano.sam.web.api.dto.VerificaCedolinoManualeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Cedolini caricati con errore: elenco dei file in errore, PDF originale conservato, rimozione, nuova lettura,
 * associazione a una mensilita' e inserimento a mano (con prova senza salvataggio).
 * <p>
 * Le azioni accettano l'id di uno qualunque dei tentativi in ERRORE del file. Il modulo cedolini le rifiuta (409) se
 * il file non e' piu' in errore o se un suo nuovo tentativo e' in elaborazione. Gli errori sui dati inviati sono
 * restituiti campo per campo ({@code errori} del Problem Details). La password della nuova lettura passa solo in
 * memoria, come quella del caricamento.
 */
@RestController
@RequestMapping(path = "/api/v1/caricamenti", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "caricamenti", description = "Caricamento asincrono dei PDF")
class CaricamentiInErroreController {

    static final String PDF_CONSERVATO = "PDF conservato del caricamento";

    private final CaricamentiInErrore inErrore;
    private final ComposizioneCaricamentiInErrore composizione;

    CaricamentiInErroreController(CaricamentiInErrore inErrore, ComposizioneCaricamentiInErrore composizione) {
        this.inErrore = inErrore;
        this.composizione = composizione;
    }

    @GetMapping("/in-errore")
    @Operation(operationId = "caricamentiInErrore", summary = "Cedolini caricati con errore ancora da risolvere",
            description = "Un elemento per file (stesso contenuto = stesso file): l'ultimo tentativo concluso del file "
                    + "e' in ERRORE e il file non e' archiviato come cedolino. Dal piu' recente; senza limite.")
    @ApiResponse(responseCode = "200", description = "File in errore")
    List<CaricamentoInErroreDto> caricamentiInErrore() {
        return composizione.elenco();
    }

    @DeleteMapping("/{caricamentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "rimuoviCaricamentoInErrore",
            summary = "Rimuove un file in errore (tentativi in errore, PDF conservato e associazione)",
            description = "Elimina tutti i caricamenti in ERRORE dello stesso file, il PDF conservato e l'eventuale "
                    + "associazione a una mensilita'. 409 se il file non e' in errore o se un nuovo tentativo e' in "
                    + "corso.")
    @ApiResponse(responseCode = "204", description = "File rimosso")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    void rimuoviCaricamentoInErrore(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId) {
        inErrore.rimuovi(new CaricamentoId(caricamentoId));
    }

    @GetMapping(value = "/{caricamentoId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "scaricaPdfCaricamento", summary = "PDF originale conservato di un file in errore",
            description = "Il PDF come caricato (anche se protetto da password), con il nome originale nell'header "
                    + "Content-Disposition (filename e filename* RFC 5987, come per i cedolini). 404 se il PDF non e' "
                    + "conservato.")
    @ApiResponse(responseCode = "200", description = "PDF come caricato (anche se protetto), con il nome originale",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                    description = "attachment; filename=\"cedolino.pdf\"; filename*=UTF-8''cedolino.pdf",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    ResponseEntity<byte[]> scaricaPdfCaricamento(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId) {
        var pdf = inErrore.pdf(new CaricamentoId(caricamentoId))
                .orElseThrow(() -> new RisorsaNonTrovataException(PDF_CONSERVATO, caricamentoId));
        var disposizione = ContentDisposition.attachment().filename(pdf.nomeFile(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.contenuto().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposizione.toString())
                .body(pdf.contenuto());
    }

    @PostMapping("/{caricamentoId}/rilettura")
    @Operation(operationId = "rileggiCaricamento",
            summary = "Rilegge il PDF conservato in un nuovo lotto (eventualmente con la password)",
            description = "Registra un nuovo caricamento dello stesso file (stato IN_CODA) in un nuovo lotto, da "
                    + "seguire in polling su GET /api/v1/caricamenti/lotti/{lotto}. La password vale solo per questa "
                    + "lettura: mai salvata ne' registrata nei log. 409 se il file non e' in errore, se un nuovo "
                    + "tentativo e' in corso o se il PDF non e' conservato.")
    @ApiResponse(responseCode = "202", description = "Lotto registrato (polling su /api/v1/caricamenti/lotti/{lotto})")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    ResponseEntity<LottoCaricamentoDto> rileggiCaricamento(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId,
            @RequestBody RichiestaRiletturaDto richiesta) {
        var password = ValidazioneCaricamento.password(richiesta.password());
        var registrati = inErrore.rileggi(new CaricamentoId(caricamentoId), password);
        var lotto = registrati.getFirst().lotto();
        var posizione = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/caricamenti/lotti/{lotto}")
                .buildAndExpand(lotto)
                .toUri();
        return ResponseEntity.accepted().location(posizione).body(Mappatura.lotto(lotto, registrati));
    }

    @PutMapping("/{caricamentoId}/attribuzione")
    @Operation(operationId = "associaCaricamento", summary = "Associa un file in errore a una mensilita' di un rapporto",
            description = "La mensilita' (anno + mese 1..14) passa da \"mancante\" a \"con cedolino in errore\". "
                    + "Sostituisce l'eventuale associazione precedente del file. 409 se per quella mensilita' esiste "
                    + "gia' un cedolino o un altro file associato.")
    @ApiResponse(responseCode = "200", description = "File aggiornato")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    CaricamentoInErroreDto associaCaricamento(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId,
            @RequestBody RichiestaAttribuzioneDto richiesta) {
        // un id non positivo non identifica alcun rapporto: e' segnalato come rapporto mancante, sul suo campo
        var rapporto = richiesta.rapportoId() == null || richiesta.rapportoId() < 1 ? null
                : new RapportoLavoroId(richiesta.rapportoId());
        return composizione.dto(inErrore.associa(new CaricamentoId(caricamentoId), rapporto, richiesta.anno(),
                richiesta.mese()));
    }

    @DeleteMapping("/{caricamentoId}/attribuzione")
    @Operation(operationId = "rimuoviAssociazioneCaricamento",
            summary = "Rimuove l'associazione del file in errore a una mensilita'")
    @ApiResponse(responseCode = "200", description = "File aggiornato")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    CaricamentoInErroreDto rimuoviAssociazioneCaricamento(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId) {
        return composizione.dto(inErrore.rimuoviAssociazione(new CaricamentoId(caricamentoId)));
    }

    @PostMapping("/{caricamentoId}/cedolino/verifica")
    @Operation(operationId = "verificaCedolinoManuale",
            summary = "Prova l'inserimento a mano senza salvare (competenza, conflitti, nuove anagrafiche, avvisi)",
            description = "Esegue l'acquisizione vera e propria e la annulla: nessun dato viene salvato. 400 con "
                    + "errori (percorsi dello schema CedolinoManuale) se mancano dati obbligatori o ci sono valori non "
                    + "validi, anche su personaId o aziendaId se l'anagrafica scelta non e' (piu') in archivio e sui "
                    + "dati anagrafici di una persona o un'azienda nuova gia' in archivio (va scelta dall'elenco).")
    @ApiResponse(responseCode = "200", description = "Esito della prova")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    VerificaCedolinoManualeDto verificaCedolinoManuale(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId,
            @RequestBody CedolinoManualeDto richiesta) {
        return Mappatura.verifica(inErrore.verifica(new CaricamentoId(caricamentoId),
                Mappatura.cedolinoManuale(richiesta)));
    }

    @PostMapping("/{caricamentoId}/cedolino")
    @Operation(operationId = "inserisciCedolinoManuale",
            summary = "Inserisce a mano il cedolino di un file in errore (stesso percorso dei lettori)",
            description = "I dati seguono lo stesso percorso dei cedolini letti dai PDF (censimento, competenza, "
                    + "duplicati, ricalcolo); il PDF conservato diventa il PDF del cedolino. Persona e azienda scelte "
                    + "dall'elenco (personaId, aziendaId) sono usate esattamente: il cedolino va al loro rapporto "
                    + "(creato se manca). 400 con errori come nella verifica; 409 se il cedolino risulta un duplicato "
                    + "o un aggiuntivo non gestito (il file resta in errore).")
    @ApiResponse(responseCode = "201", description = "Cedolino acquisito; il caricamento e' ACQUISITO")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    ResponseEntity<CaricamentoDto> inserisciCedolinoManuale(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long caricamentoId,
            @RequestBody CedolinoManualeDto richiesta) {
        var caricamento = inErrore.inserisci(new CaricamentoId(caricamentoId), Mappatura.cedolinoManuale(richiesta));
        if (caricamento.cedolinoId() == null) {
            // cedolino eliminato subito dopo l'acquisizione (operazione concorrente): nessun dettaglio da indicare
            return ResponseEntity.status(HttpStatus.CREATED).body(Mappatura.caricamento(caricamento));
        }
        var posizione = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/cedolini/{cedolinoId}")
                .buildAndExpand(caricamento.cedolinoId().valore())
                .toUri();
        return ResponseEntity.created(posizione).body(Mappatura.caricamento(caricamento));
    }
}
