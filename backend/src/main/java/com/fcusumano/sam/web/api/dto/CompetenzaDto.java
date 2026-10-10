package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.shared.TipoMensilita;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Competenza del cedolino: anno + mese 1..14 oppure progressivo TFR (fine rapporto, senza mese).
 *
 * @param chiave chiave univoca nel rapporto, es. "2024.01", "2024.13", "2020.TFR01"
 */
@Schema(name = "Competenza")
public record CompetenzaDto(String chiave, TipoMensilita tipo, int anno, Integer mese, Integer progressivoTfr) {
}
