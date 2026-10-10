package com.fcusumano.sam.cedolini.application.estrazione;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dati estratti da un cedolino: rispecchia lo schema JSON dello structured output richiesto al modello.
 * <p>
 * Convenzioni (da riportare nel prompt e nelle descrizioni dello schema):
 * <ul>
 *   <li>tutti i campi sono nullable: null = non presente sul documento (mai inventare valori);</li>
 *   <li>date in formato ISO {@code yyyy-MM-dd} (il modello normalizza i formati stampati: "1/05/2020", "15011985", ...);
 *       se e' stampato solo mese/anno si usa il primo giorno del mese;</li>
 *   <li>importi come numeri decimali positivi con il punto come separatore; la colonna indica competenza o trattenuta;</li>
 *   <li>i totali vanno letti sull'ultimo foglio ("Segue da foglio numero ...");</li>
 *   <li>nessun dato bancario (IBAN, ABI/CAB, conto) deve essere estratto.</li>
 * </ul>
 *
 * @param numeroCedoliniNelDocumento numero di cedolini distinti nel PDF (piu' fogli dello stesso cedolino = 1);
 *                                   un valore diverso da 1 e' un errore di caricamento
 * @param confidenza                 confidenza complessiva e per campo chiave
 * @param campiNonTrovati            nomi dei campi chiave non trovati (es. "datoreLavoro.partitaIva")
 */
public record CedolinoEstratto(
        Integer numeroCedoliniNelDocumento,
        Dipendente dipendente,
        DatoreLavoro datoreLavoro,
        DatiContrattuali datiContrattuali,
        Periodo periodo,
        Totali totali,
        Retribuzione retribuzione,
        List<VoceEstratta> voci,
        List<RateoEstratto> ratei,
        Tfr tfr,
        Presenze presenze,
        Scatti scatti,
        BuoniPasto buoniPasto,
        Progressivi progressivi,
        Boolean contieneTredicesima,
        Boolean contieneQuattordicesima,
        Confidenza confidenza,
        List<String> campiNonTrovati) {

    /** Tipo di cedolino riconosciuto dal modello. */
    public enum TipoCedolinoEstratto {
        /** Mensilita' ordinaria. */
        ORDINARIA,
        /** Tredicesima separata (anche "Dicembre ... AGG." con voce "13ma Mensilita'"): mese 13, non dicembre. */
        TREDICESIMA,
        /** Quattordicesima separata: mese 14. */
        QUATTORDICESIMA,
        /** Emesso dopo la data di cessazione (liquidazione: ferie non godute, ratei, TFR). */
        FINE_RAPPORTO,
        /** Cedolino aggiuntivo nello stesso mese (premi, arretrati) che non e' tredicesima/quattordicesima. */
        AGGIUNTIVA
    }

    /** @param dataNascita ISO yyyy-MM-dd */
    public record Dipendente(String codiceFiscale, String nome, String cognome, String dataNascita, String codiceDipendente) {
    }

    public record DatoreLavoro(
            String codiceFiscale,
            String partitaIva,
            String posizioneInps,
            String ragioneSociale,
            String indirizzo,
            String codiceAzienda) {
    }

    /**
     * @param dataAssunzione ISO yyyy-MM-dd
     * @param dataCessazione ISO yyyy-MM-dd
     * @param ccnl           testo del CCNL come stampato
     */
    public record DatiContrattuali(String dataAssunzione, String dataCessazione, String qualifica, String livello, String ccnl) {
    }

    /**
     * @param anno              anno del periodo di retribuzione stampato
     * @param mese              mese (1..12) del periodo di retribuzione stampato (non quello delle variabili)
     * @param dataElaborazione  ISO yyyy-MM-dd
     * @param annoVariabili     anno delle voci variabili se riferite a un altro mese ("Variabili di 10/2021")
     * @param meseVariabili     mese delle voci variabili
     */
    public record Periodo(
            Integer anno,
            Integer mese,
            TipoCedolinoEstratto tipo,
            String dataElaborazione,
            Integer annoVariabili,
            Integer meseVariabili) {
    }

    /** @param arrotondamento unico importo che puo' essere negativo (arrotondamento del netto in diminuzione) */
    public record Totali(
            BigDecimal lordo,
            BigDecimal totaleCompetenze,
            BigDecimal totaleTrattenute,
            BigDecimal arrotondamento,
            BigDecimal netto) {
    }

    public record Retribuzione(
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

    public record VoceEstratta(
            String codice,
            String descrizione,
            CategoriaVoce categoria,
            BigDecimal quantita,
            String unita,
            BigDecimal base,
            BigDecimal importoCompetenza,
            BigDecimal importoTrattenuta) {
    }

    public record RateoEstratto(
            TipoRateo tipo,
            UnitaMisura unita,
            BigDecimal residuoAnnoPrecedente,
            BigDecimal maturato,
            BigDecimal goduto,
            BigDecimal saldo) {
    }

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

    public record Presenze(BigDecimal giorniLavorati, BigDecimal oreLavorate, BigDecimal giorniRetribuiti) {
    }

    /** @param dataProssimoScatto ISO yyyy-MM-dd (primo del mese se stampato solo mese/anno) */
    public record Scatti(Integer numeroScatti, String dataProssimoScatto) {
    }

    public record BuoniPasto(Boolean presenti, BigDecimal numero, BigDecimal valoreUnitario, BigDecimal importo) {
    }

    public record Progressivi(BigDecimal imponibilePrevidenziale, BigDecimal imponibileIrpef, BigDecimal irpefTrattenuta) {
    }

    /** @param complessiva 0..1 */
    public record Confidenza(BigDecimal complessiva, List<ConfidenzaCampo> campi) {
    }

    /** @param campo nome del campo chiave (es. "periodo.mese"); @param valore 0..1 */
    public record ConfidenzaCampo(String campo, BigDecimal valore) {
    }
}
