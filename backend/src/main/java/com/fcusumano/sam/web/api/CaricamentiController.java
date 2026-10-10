package com.fcusumano.sam.web.api;

import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.CaricamentoDto;
import com.fcusumano.sam.web.api.dto.LottoCaricamentoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Caricamento asincrono dei PDF: la POST registra i file e ritorna subito (202), lo stato si segue in polling sul
 * lotto.
 * <p>
 * La password del lotto passa solo in memoria al modulo cedolini: non viene registrata nei log, restituita o inserita
 * nei messaggi di errore.
 */
@RestController
@RequestMapping(path = "/api/v1/caricamenti", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "caricamenti", description = "Caricamento asincrono dei PDF")
class CaricamentiController {

    static final String LOTTO = "Lotto di caricamento";

    private final CaricamentoCedolini caricamento;
    private final ValidazioneCaricamento validazione;

    CaricamentiController(CaricamentoCedolini caricamento, ValidazioneCaricamento validazione) {
        this.caricamento = caricamento;
        this.validazione = validazione;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "caricaCedolini", summary = "Carica uno o piu' PDF (elaborazione asincrona)",
            description = "Registra i file (stato IN_CODA) e ritorna subito. Lo stato di ogni file si segue in "
                    + "polling su GET /api/v1/caricamenti/lotti/{lotto} finche' completato e' true. Solo PDF, "
                    + "massimo 25MB per file e 500MB per caricamento.")
    @ApiResponse(responseCode = "202", description = "Caricamenti registrati")
    @Risposte.RichiestaNonValida
    @Risposte.TroppoGrande
    ResponseEntity<LottoCaricamentoDto> caricaCedolini(
            @RequestPart("files")
            @ArraySchema(minItems = 1, schema = @Schema(type = "string", format = "binary"))
            List<MultipartFile> files,
            @RequestPart(name = "password", required = false)
            @Parameter(description = "Password di apertura dei PDF protetti, valida per tutto il lotto "
                    + "(facoltativa). Il backend prova questa, poi le password configurate e i codici fiscali delle "
                    + "persone censite. Mai salvata ne' registrata nei log.",
                    schema = @Schema(type = "string", format = "password", maxLength =
                            ValidazioneCaricamento.LUNGHEZZA_MASSIMA_PASSWORD,
                            accessMode = Schema.AccessMode.WRITE_ONLY))
            String password) {
        var passwordLotto = ValidazioneCaricamento.password(password);
        var registrati = caricamento.avvia(validazione.fileCaricati(files), passwordLotto);
        var lotto = registrati.getFirst().lotto();
        var posizione = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/lotti/{lotto}")
                .buildAndExpand(lotto)
                .toUri();
        return ResponseEntity.accepted().location(posizione).body(Mappatura.lotto(lotto, registrati));
    }

    @GetMapping
    @Operation(operationId = "caricamentiRecenti", summary = "Ultimi caricamenti")
    @ApiResponse(responseCode = "200", description = "Caricamenti dal piu' recente")
    @Risposte.RichiestaNonValida
    List<CaricamentoDto> caricamentiRecenti(
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = Messaggi.LIMITE_NON_VALIDO)
            @Max(value = 200, message = Messaggi.LIMITE_NON_VALIDO)
            int limite) {
        return caricamento.recenti(limite).stream().map(Mappatura::caricamento).toList();
    }

    @GetMapping("/lotti/{lotto}")
    @Operation(operationId = "statoLotto", summary = "Stato dei caricamenti di un lotto (polling)")
    @ApiResponse(responseCode = "200", description = "Stato del lotto")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    LottoCaricamentoDto statoLotto(@PathVariable UUID lotto) {
        var caricamenti = caricamento.statoLotto(lotto);
        if (caricamenti.isEmpty()) {
            throw new RisorsaNonTrovataException(LOTTO, lotto);
        }
        return Mappatura.lotto(lotto, caricamenti);
    }
}
