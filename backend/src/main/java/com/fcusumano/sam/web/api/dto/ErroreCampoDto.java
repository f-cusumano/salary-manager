package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Errore su un campo della richiesta (elenco {@code errori} delle risposte 400).
 *
 * @param campo     percorso nello schema della richiesta (es. "totali.netto", "voci[2].descrizione", "mese"); null per
 *                  un errore generale
 * @param messaggio messaggio per l'utente; non ripete mai i valori inviati
 */
@Schema(name = "ErroreCampo")
public record ErroreCampoDto(String campo, String messaggio) {
}
