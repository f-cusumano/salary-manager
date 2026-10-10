package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mensilita' a cui l'utente ha associato un file in errore.
 *
 * @param persona            etichetta della persona (come {@code PersonaSintesi.etichetta})
 * @param azienda            etichetta del rapporto (come {@code RapportoSintesi.etichetta})
 * @param titolo             titolo della mensilita' pronto per la UI, es. "Maggio 2023"
 * @param competenzaPresente nel frattempo e' stato acquisito un cedolino per questa mensilita'
 */
@Schema(name = "AttribuzioneCaricamento")
public record AttribuzioneCaricamentoDto(
        long rapportoId,
        long personaId,
        String persona,
        String azienda,
        CompetenzaDto competenza,
        String titolo,
        boolean competenzaPresente) {
}
