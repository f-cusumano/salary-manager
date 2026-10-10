package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Voce dell'elenco aziende di una persona.
 *
 * @param etichetta      ragione sociale; solo in caso di omonimia tra le aziende della persona si aggiunge
 *                       " (P.IVA ...)", in mancanza " (CF ...)" o " (Pos. INPS ...)"
 * @param haSegnalazioni mostra il triangolo arancione accanto all'azienda
 */
@Schema(name = "RapportoSintesi")
public record RapportoSintesiDto(
        long id,
        long personaId,
        AziendaSintesiDto azienda,
        String etichetta,
        PeriodoDto periodo,
        boolean attuale,
        boolean haSegnalazioni,
        boolean ccnlDaIndicare) {
}
