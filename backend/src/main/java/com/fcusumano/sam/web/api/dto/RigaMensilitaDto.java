package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Riga della lista mensilita': cedolino presente (espandibile), mensilita' con un file in errore associato
 * dall'utente o mensilita' mancante (solo titolo).
 *
 * @param chiave        uguale a {@code competenza.chiave}
 * @param titolo        pronto per la UI: "Gennaio 2024", "Tredicesima 2024", "Fine rapporto 1 – Aprile 2020"
 * @param ratei         residui stampati sul cedolino (solo i ratei con saldo stampato)
 * @param caricamentoId file in errore associato alla mensilita' (solo IN_ERRORE)
 * @param motivoErrore  motivo dell'errore del file associato (solo IN_ERRORE; null anche se non registrato)
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
        String nomeFile,
        Long caricamentoId,
        MotivoErrore motivoErrore) {

    /**
     * Cedolino caricato, mensilita' attesa ma assente oppure mensilita' senza cedolino con un file in errore associato.
     * Per la stessa chiave PRESENTE prevale su IN_ERRORE e IN_ERRORE su MANCANTE.
     */
    @Schema(name = "StatoRigaMensilita")
    public enum Stato {
        PRESENTE,
        MANCANTE,
        IN_ERRORE
    }

    @Schema(name = "SaldoRateoCedolino")
    public record SaldoRateoCedolino(TipoRateo tipo, UnitaMisura unita, BigDecimal saldo) {
    }
}
