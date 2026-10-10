package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.web.api.dto.NoteRapportoDto;
import com.fcusumano.sam.web.api.dto.RapportoDettaglioDto;
import com.fcusumano.sam.web.api.dto.SceltaCcnlDto;
import com.fcusumano.sam.web.error.RichiestaNonValidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dettaglio del rapporto (segnalazioni, riepilogo, mensilita'), note e scelta del CCNL.
 */
@RestController
@RequestMapping(path = "/api/v1/rapporti/{rapportoId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "rapporti", description = "Dettaglio rapporto (segnalazioni, riepilogo, mensilita'), note e CCNL")
class RapportiController {

    private final ComposizioneRapporti composizione;
    private final GestioneRapporti gestione;
    private final CatalogoCcnl catalogoCcnl;

    RapportiController(ComposizioneRapporti composizione, GestioneRapporti gestione, CatalogoCcnl catalogoCcnl) {
        this.composizione = composizione;
        this.gestione = gestione;
        this.catalogoCcnl = catalogoCcnl;
    }

    @GetMapping
    @Operation(operationId = "dettaglioRapporto", summary = "Dettaglio di un rapporto (3 aree)",
            description = "Segnalazioni, riepilogo, CCNL, note e lista mensilita' (cedolini presenti e mensilita' "
                    + "mancanti) in ordine cronologico inverso.")
    @ApiResponse(responseCode = "200", description = "Dettaglio del rapporto")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    RapportoDettaglioDto dettaglioRapporto(
            @PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long rapportoId) {
        return composizione.dettaglio(rapportoId);
    }

    @PutMapping("/note")
    @Operation(operationId = "aggiornaNote", summary = "Aggiorna le note del rapporto")
    @ApiResponse(responseCode = "200", description = "Note salvate")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    NoteRapportoDto aggiornaNote(@PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long rapportoId,
                                 @Valid @RequestBody NoteRapportoDto richiesta) {
        var rapporto = gestione.aggiornaNote(new RapportoLavoroId(rapportoId), richiesta.note());
        return new NoteRapportoDto(rapporto.note());
    }

    @PutMapping("/ccnl")
    @Operation(operationId = "impostaCcnl", summary = "Imposta (o cambia) il CCNL del rapporto",
            description = "La scelta vale per tutti i cedolini del rapporto, presenti e futuri, e resta modificabile. "
                    + "Il cambio fa ricalcolare da zero riepilogo e segnalazioni: la risposta e' il dettaglio "
                    + "aggiornato.")
    @ApiResponse(responseCode = "200", description = "Dettaglio del rapporto ricalcolato")
    @Risposte.RichiestaNonValida
    @Risposte.NonTrovato
    @Risposte.Conflitto
    RapportoDettaglioDto impostaCcnl(@PathVariable @Min(value = 1, message = Messaggi.ID_NON_VALIDO) long rapportoId,
                                     @Valid @RequestBody SceltaCcnlDto richiesta) {
        var codice = richiesta.ccnlCodice().strip();
        var ccnl = catalogoCcnl.trova(codice).orElseThrow(() -> new RichiestaNonValidaException(
                "CCNL sconosciuto: scegli un codice dall'elenco dei CCNL."));
        gestione.impostaCcnl(new RapportoLavoroId(rapportoId), ccnl.codice());
        return composizione.dettaglio(rapportoId);
    }
}
