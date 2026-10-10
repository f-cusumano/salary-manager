package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Stato dei caricamenti di un lotto.
 *
 * @param completato true quando tutti i caricamenti sono in uno stato terminale (polling terminato)
 */
@Schema(name = "LottoCaricamento")
public record LottoCaricamentoDto(UUID lotto, boolean completato, List<CaricamentoDto> caricamenti) {
}
