package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Dettaglio della busta per aree tematiche.
 *
 * @param meseVariabili   mese a cui si riferiscono le voci variabili, se stampato
 * @param confidenza      confidenza complessiva dell'estrazione (0..1), null per i cedolini inseriti a mano
 * @param inseritoAMano   cedolino inserito a mano da un file in errore (nessuna lettura)
 * @param avvisiEstrazione avvisi dell'estrazione (controlli di coerenza non superati, dati bancari rimossi, note)
 * @param vociNonRetributive rimborsi, diarie, buoni pasto (fuori dalla RAL)
 */
@Schema(name = "CedolinoDettaglio")
public record CedolinoDettaglioDto(
        long id,
        long rapportoId,
        String titolo,
        CompetenzaDto competenza,
        YearMonth periodoRetribuzione,
        YearMonth meseVariabili,
        LocalDate dataElaborazione,
        String nomeFile,
        OffsetDateTime acquisitoIl,
        BigDecimal confidenza,
        boolean inseritoAMano,
        List<String> campiNonTrovati,
        List<String> avvisiEstrazione,
        Inquadramento inquadramento,
        Totali totali,
        ElementiRetributivi elementiRetributivi,
        Presenze presenze,
        List<VoceDto> competenze,
        List<VoceDto> trattenutePrevidenziali,
        List<VoceDto> trattenuteFiscali,
        List<VoceDto> altreTrattenute,
        List<VoceDto> vociNonRetributive,
        List<VoceDto> vociInformative,
        Tfr tfr,
        List<Rateo> ratei,
        Scatti scatti,
        BuoniPasto buoniPasto,
        Progressivi progressivi,
        boolean contieneTredicesima,
        boolean contieneQuattordicesima) {

    @Schema(name = "InquadramentoCedolino")
    public record Inquadramento(
            String qualifica,
            String livello,
            String codiceDipendente,
            String ccnlTesto,
            LocalDate dataAssunzione,
            LocalDate dataCessazione) {
    }

    /**
     * @param arrotondamento puo' essere negativo
     */
    @Schema(name = "TotaliCedolino")
    public record Totali(
            BigDecimal lordo,
            BigDecimal totaleCompetenze,
            BigDecimal totaleTrattenute,
            BigDecimal arrotondamento,
            BigDecimal netto) {
    }

    @Schema(name = "ElementiRetributiviCedolino")
    public record ElementiRetributivi(
            BigDecimal minimo,
            BigDecimal contingenza,
            BigDecimal superminimo,
            Boolean superminimoAssorbibile,
            BigDecimal importoScatti,
            BigDecimal altriElementiFissi,
            BigDecimal retribuzioneBase,
            BigDecimal totale,
            BigDecimal pagaOraria,
            BigDecimal pagaGiornaliera,
            BigDecimal divisoreOrario,
            BigDecimal divisoreGiornaliero) {
    }

    /**
     * Area presenze: riepilogo + voci di presenza/assenza.
     */
    @Schema(name = "PresenzeCedolino")
    public record Presenze(
            BigDecimal giorniLavorati,
            BigDecimal oreLavorate,
            BigDecimal giorniRetribuiti,
            List<VoceDto> voci) {
    }

    @Schema(name = "TfrCedolino")
    public record Tfr(
            BigDecimal fondo31DicembreAnnoPrecedente,
            BigDecimal rivalutazione,
            BigDecimal impostaSostitutiva,
            BigDecimal quotaAnno,
            BigDecimal quotaMese,
            BigDecimal quotaFondi,
            BigDecimal quotaBusta,
            BigDecimal anticipi,
            BigDecimal retribuzioneUtile,
            BigDecimal liquidatoLordo,
            BigDecimal ritenuta) {
    }

    @Schema(name = "RateoCedolino")
    public record Rateo(
            TipoRateo tipo,
            UnitaMisura unita,
            BigDecimal residuoAnnoPrecedente,
            BigDecimal maturato,
            BigDecimal goduto,
            BigDecimal saldo) {
    }

    @Schema(name = "ScattiCedolino")
    public record Scatti(Integer numeroScatti, LocalDate prossimoScatto) {
    }

    @Schema(name = "BuoniPastoCedolino")
    public record BuoniPasto(boolean presenti, BigDecimal numero, BigDecimal valoreUnitario, BigDecimal importo) {
    }

    @Schema(name = "ProgressiviCedolino")
    public record Progressivi(
            BigDecimal imponibilePrevidenziale,
            BigDecimal imponibileIrpef,
            BigDecimal irpefTrattenuta) {
    }
}
