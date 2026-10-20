package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.TipoVoceLiquidazione;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Riepilogo derivato del rapporto (area 2 del dettaglio).
 *
 * @param buoniPasto "Buoni pasto: SI/NO": SI se presenti in almeno uno degli ultimi 3 cedolini ordinari
 * @param ratei      residui di ferie/permessi: esposti solo per il rapporto attuale (saldi vuoti altrimenti)
 * @param liquidazione stima "Se andassi via oggi": solo per il rapporto attuale o per l'ultimo rapporto della persona
 *                     finche' il TFR non e' liquidato; null altrimenti
 */
@Schema(name = "Riepilogo")
public record RiepilogoDto(
        OffsetDateTime calcolatoIl,
        Anzianita anzianita,
        Scatti scatti,
        Retribuzione retribuzione,
        boolean buoniPasto,
        RateiRiepilogo ratei,
        TfrRiepilogo tfr,
        Liquidazione liquidazione) {

    @Schema(name = "Anzianita")
    public record Anzianita(
            YearMonth dal,
            YearMonth al,
            boolean inCorso,
            int anni,
            int mesi,
            FonteDato fonteInizio,
            FonteDato fonteFine) {
    }

    /**
     * @param fonteAttuali   origine di {@code attuali}: CEDOLINI se stampato, CCNL se stimato dalle regole del CCNL,
     *                       NON_DISPONIBILE
     * @param attualiStimati true se {@code attuali} e' una stima (regole del CCNL, o numero stampato su un cedolino
     *                       non recente)
     */
    @Schema(name = "Scatti")
    public record Scatti(
            Integer attuali,
            Integer massimo,
            LocalDate prossimoScatto,
            FonteDato fonteProssimo,
            FonteDato fonteAttuali,
            boolean attualiStimati) {
    }

    /**
     * @param cedolinoRiferimentoId cedolino ordinario usato come riferimento per la RAL
     */
    @Schema(name = "Retribuzione")
    public record Retribuzione(
            BigDecimal ral,
            BigDecimal lordoMensile,
            Integer mensilita,
            BigDecimal minimo,
            BigDecimal contingenza,
            BigDecimal superminimo,
            BigDecimal importoScatti,
            BigDecimal altriElementiFissi,
            BigDecimal retribuzioneBase,
            boolean stimata,
            String nota,
            Long cedolinoRiferimentoId) {
    }

    /**
     * @param nota base usata per la stima del valore
     */
    @Schema(name = "SaldoRateo")
    public record SaldoRateo(
            TipoRateo tipo,
            UnitaMisura unita,
            BigDecimal saldo,
            BigDecimal valoreStimato,
            String nota) {
    }

    @Schema(name = "RateiRiepilogo")
    public record RateiRiepilogo(Long cedolinoRiferimentoId, List<SaldoRateo> saldi) {
    }

    /**
     * @param totaleAccumulato    TFR in azienda + quote versate ai fondi risultanti dai cedolini caricati; 0 se liquidato
     * @param stimaNetto          stima del netto, solo sulla quota in azienda; null se liquidato
     * @param quotaMensileStimata true se la quota mensile non e' stampata ed e' stimata
     * @param liquidato           true se un cedolino di fine rapporto riporta il TFR liquidato
     * @param importoLiquidato    TFR liquidato lordo; null se non liquidato
     * @param nettoLiquidato      TFR liquidato al netto della ritenuta stampata; null se non liquidato o senza ritenuta
     */
    @Schema(name = "TfrRiepilogo")
    public record TfrRiepilogo(
            BigDecimal totaleAccumulato,
            BigDecimal quotaMensile,
            BigDecimal quotaInAzienda,
            BigDecimal quotaFondi,
            BigDecimal quotaBusta,
            BigDecimal anticipi,
            BigDecimal stimaNetto,
            BigDecimal aliquotaStimata,
            boolean stimato,
            YearMonth riferimento,
            String nota,
            boolean quotaMensileStimata,
            boolean liquidato,
            BigDecimal importoLiquidato,
            BigDecimal nettoLiquidato) {
    }

    /**
     * Stima della liquidazione alla data di riferimento (sempre una stima, preavviso lavorato).
     *
     * @param dataRiferimento            cessazione, o fine del mese dell'ultimo cedolino ordinario
     * @param totaleNetto                somma dei netti stimabili
     * @param completa                   false se il netto di almeno una voce non e' stimabile (totali parziali)
     * @param aliquotaContributiStampata true se l'aliquota INPS e' letta dal cedolino
     * @param aliquotaIrpefMarginale     null se la RAL non e' nota
     * @param aliquotaTfr                aliquota media della tassazione separata del TFR; null se non determinabile
     * @param nota                       ipotesi generali della stima
     */
    @Schema(name = "Liquidazione")
    public record Liquidazione(
            LocalDate dataRiferimento,
            List<VoceLiquidazione> voci,
            BigDecimal totaleLordo,
            BigDecimal totaleContributi,
            BigDecimal totaleImposte,
            BigDecimal totaleNetto,
            boolean completa,
            BigDecimal aliquotaContributi,
            boolean aliquotaContributiStampata,
            BigDecimal aliquotaIrpefMarginale,
            BigDecimal aliquotaTfr,
            String nota) {
    }

    /**
     * @param quantita giorni/ore (ferie e permessi) o mesi (ratei di tredicesima e quattordicesima); null per il TFR
     * @param unita    unita' di ferie e permessi; null per TFR e ratei
     * @param nota     base del calcolo o motivo per cui l'importo non e' stimabile
     */
    @Schema(name = "VoceLiquidazione")
    public record VoceLiquidazione(
            TipoVoceLiquidazione tipo,
            BigDecimal quantita,
            UnitaMisura unita,
            BigDecimal lordo,
            BigDecimal contributi,
            BigDecimal imposte,
            BigDecimal netto,
            String nota) {
    }
}
