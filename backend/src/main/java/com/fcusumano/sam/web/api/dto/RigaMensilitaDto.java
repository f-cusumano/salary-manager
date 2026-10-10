package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Riga della lista mensilita': cedolino presente (espandibile) o mensilita' mancante (solo titolo).
 *
 * @param chiave uguale a {@code competenza.chiave}
 * @param titolo pronto per la UI: "Gennaio 2024", "Tredicesima 2024", "Fine rapporto 1 – Aprile 2020"
 * @param ratei  residui stampati sul cedolino (solo i ratei con saldo stampato)
 */
@Schema(name = "RigaMensilita")
public record RigaMensilitaDto(
        String chiave,
        Stato stato,
        String titolo,
        CompetenzaDto competenza,
        YearMonth periodoRetribuzione,
        Long cedolinoId,
        BigDecimal netto,
        BigDecimal lordo,
        List<SaldoRateoCedolino> ratei,
        boolean scattoInBusta,
        boolean aumentoFuoriScatti,
        String nomeFile) {

    /** Cedolino caricato oppure mensilita' attesa ma assente. */
    @Schema(name = "StatoRigaMensilita")
    public enum Stato {
        PRESENTE,
        MANCANTE
    }

    @Schema(name = "SaldoRateoCedolino")
    public record SaldoRateoCedolino(TipoRateo tipo, UnitaMisura unita, BigDecimal saldo) {
    }
}
