package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.shared.TipoMensilita;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Cedolino inserito a mano per un file in errore: stessi campi e percorsi dello schema di lettura
 * ({@code prompts/cedolino-schema-v1.json}) con tipi JSON. null = non stampato; liste assenti trattate come vuote.
 * Riusa gli schemi del dettaglio dove coincidono (totali, elementi retributivi, ratei, TFR, buoni pasto, progressivi).
 * I percorsi dei campi sono quelli usati negli {@code errori} delle risposte 400 (es. {@code voci[2].descrizione}).
 *
 * @param voci  voci nell'ordine del cedolino
 * @param ratei al piu' un rateo per tipo e unita'
 * @param tfr   null se il cedolino non riporta dati TFR
 */
@Schema(name = "CedolinoManuale")
public record CedolinoManualeDto(
        DipendenteManuale dipendente,
        DatoreLavoroManuale datoreLavoro,
        DatiContrattualiManuali datiContrattuali,
        PeriodoManuale periodo,
        CedolinoDettaglioDto.Totali totali,
        CedolinoDettaglioDto.ElementiRetributivi retribuzione,
        List<VoceManuale> voci,
        List<CedolinoDettaglioDto.Rateo> ratei,
        CedolinoDettaglioDto.Tfr tfr,
        PresenzeManuali presenze,
        ScattiManuali scatti,
        CedolinoDettaglioDto.BuoniPasto buoniPasto,
        CedolinoDettaglioDto.Progressivi progressivi,
        boolean contieneTredicesima,
        boolean contieneQuattordicesima) {

    /** @param codiceFiscale 16 caratteri, oppure 11 cifre (codice provvisorio, con avviso) */
    @Schema(name = "DipendenteManuale")
    public record DipendenteManuale(
            String codiceFiscale,
            String nome,
            String cognome,
            LocalDate dataNascita,
            String codiceDipendente) {
    }

    /**
     * @param codiceFiscale 11 cifre o 16 caratteri
     * @param partitaIva    11 cifre (prefisso IT facoltativo)
     */
    @Schema(name = "DatoreLavoroManuale")
    public record DatoreLavoroManuale(
            String codiceFiscale,
            String partitaIva,
            String posizioneInps,
            String posizioneInail,
            String ragioneSociale,
            String indirizzo,
            String codiceAzienda) {
    }

    /** @param ccnl CCNL come stampato sul cedolino (testo libero) */
    @Schema(name = "DatiContrattualiManuali")
    public record DatiContrattualiManuali(
            LocalDate dataAssunzione,
            LocalDate dataCessazione,
            String qualifica,
            String livello,
            String ccnl,
            String codiceCnel) {
    }

    /**
     * @param mese          mese di riferimento 1..12 (tredicesima e quattordicesima si indicano con {@code tipo})
     * @param meseVariabili mese a cui si riferiscono le voci variabili, se stampato
     */
    @Schema(name = "PeriodoManuale")
    public record PeriodoManuale(
            Integer anno,
            @Schema(minimum = "1", maximum = "12") Integer mese,
            TipoMensilita tipo,
            LocalDate dataElaborazione,
            YearMonth meseVariabili) {
    }

    /**
     * @param unita unita' della quantita' come stampata (ore, giorni, km, %...)
     * @param base  base o tariffa unitaria
     */
    @Schema(name = "VoceManuale")
    public record VoceManuale(
            String codice,
            String descrizione,
            CategoriaVoce categoria,
            BigDecimal quantita,
            String unita,
            BigDecimal base,
            BigDecimal importoCompetenza,
            BigDecimal importoTrattenuta) {
    }

    @Schema(name = "PresenzeManuali")
    public record PresenzeManuali(BigDecimal giorniLavorati, BigDecimal oreLavorate, BigDecimal giorniRetribuiti) {
    }

    @Schema(name = "ScattiManuali")
    public record ScattiManuali(Integer numeroScatti, LocalDate dataProssimoScatto) {
    }
}
