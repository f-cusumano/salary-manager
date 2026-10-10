package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Voce dell'elenco aziende (scelta del datore di lavoro nell'inserimento a mano).
 *
 * @param posizioneInps matricola INPS come registrata (normalizzata a 10 cifre quando presenti)
 * @param etichetta     ragione sociale; solo in caso di omonimia tra le aziende del workspace si aggiunge un
 *                      identificativo: "Ragione sociale (P.IVA 01234567890)", in mancanza "(CF ...)", poi
 *                      "(Pos. INPS ...)"
 */
@Schema(name = "AziendaSelezionabile")
public record AziendaSelezionabileDto(
        long id,
        String ragioneSociale,
        String partitaIva,
        String codiceFiscale,
        String posizioneInps,
        String indirizzo,
        String codiceAzienda,
        String etichetta) {
}
