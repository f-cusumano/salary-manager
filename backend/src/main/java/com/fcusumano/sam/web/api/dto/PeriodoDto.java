package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.anagrafica.FontePeriodo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;

/**
 * Periodo del rapporto: {@code a} e' null se il rapporto e' attuale.
 */
@Schema(name = "Periodo")
public record PeriodoDto(YearMonth da, YearMonth a, FontePeriodo fonteInizio, FontePeriodo fonteFine) {
}
