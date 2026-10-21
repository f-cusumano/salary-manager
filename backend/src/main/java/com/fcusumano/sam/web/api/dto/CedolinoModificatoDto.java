package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cedolino archiviato di nuovo con i dati modificati a mano.
 *
 * @param cedolinoId        nuovo id del cedolino (il precedente non esiste piu')
 * @param rapportoId        rapporto del cedolino: diverso dal precedente se sono cambiate persona o azienda
 * @param titolo            titolo della competenza pronto per la UI
 * @param rapportoEliminato il rapporto precedente e' stato eliminato perche' rimasto senza cedolini
 */
@Schema(name = "CedolinoModificato")
public record CedolinoModificatoDto(
        long cedolinoId,
        long rapportoId,
        CompetenzaDto competenza,
        String titolo,
        boolean rapportoEliminato) {
}
