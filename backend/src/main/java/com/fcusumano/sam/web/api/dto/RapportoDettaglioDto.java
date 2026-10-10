package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Dettaglio di un rapporto (3 aree: segnalazioni, riepilogo, mensilita').
 *
 * @param ccnl      null se il CCNL e' da indicare
 * @param riepilogo null se non ancora calcolato
 * @param mensilita cedolini presenti e mensilita' mancanti, in ordine cronologico inverso
 */
@Schema(name = "RapportoDettaglio")
public record RapportoDettaglioDto(
        long id,
        PersonaSintesiDto persona,
        AziendaSintesiDto azienda,
        String etichetta,
        PeriodoDto periodo,
        boolean attuale,
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        CcnlDto ccnl,
        boolean ccnlDaIndicare,
        boolean ccnlSceltoDaUtente,
        String note,
        List<SegnalazioneDto> segnalazioni,
        RiepilogoDto riepilogo,
        List<RigaMensilitaDto> mensilita) {
}
