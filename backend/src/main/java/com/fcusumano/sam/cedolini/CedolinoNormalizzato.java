package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Cedolino normalizzato: tutti i dati che servono a calcoli e segnalazioni. I campi non stampati sono null
 * (i sotto-record non sono mai null: si usano le costanti VUOTI/ASSENTI). Liste mai null.
 *
 * @param competenza               anno + mese 1..14 oppure progressivo TFR (fine rapporto)
 * @param periodoRetribuzione      mese di retribuzione stampato (per tredicesima/quattordicesima: mese di erogazione);
 *                                 e' il periodo del cedolino, non quello delle variabili
 * @param meseVariabili            mese a cui si riferiscono le voci variabili ("Variabili di 10/2021"), se stampato
 * @param dataAssunzione           data stampata (prevale sulle date dedotte)
 * @param dataCessazione           data stampata
 * @param codiceDipendente         codice/matricola del dipendente se stampato (attributo aggiornabile, non chiave)
 * @param ccnlTesto                testo del CCNL come stampato
 * @param lordo                    totale lordo (totale competenze) letto sull'ultimo foglio
 * @param arrotondamento           arrotondamento del netto (positivo o negativo) se stampato
 * @param numeroScatti             numero di scatti maturati se stampato
 * @param prossimoScatto           data del prossimo scatto se stampata (prevale sul calcolo da CCNL)
 * @param contieneTredicesima      true se il cedolino (ordinario o di fine rapporto) paga la tredicesima o suoi ratei:
 *                                 es. tredicesima nel cedolino di dicembre, ratei liquidati a fine rapporto
 * @param contieneQuattordicesima  true se il cedolino (ordinario o di fine rapporto) paga la quattordicesima o suoi ratei
 * @param tfr                      null se il cedolino non riporta dati TFR
 */
public record CedolinoNormalizzato(
        CedolinoId id,
        RapportoLavoroId rapportoId,
        PersonaId personaId,
        AziendaId aziendaId,
        Competenza competenza,
        YearMonth periodoRetribuzione,
        YearMonth meseVariabili,
        LocalDate dataElaborazione,
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        String codiceDipendente,
        String ccnlTesto,
        BigDecimal lordo,
        BigDecimal totaleCompetenze,
        BigDecimal totaleTrattenute,
        BigDecimal arrotondamento,
        BigDecimal netto,
        ElementiRetributivi elementiRetributivi,
        List<Voce> voci,
        List<Rateo> ratei,
        DatiTfr tfr,
        Presenze presenze,
        Integer numeroScatti,
        LocalDate prossimoScatto,
        BuoniPasto buoniPasto,
        boolean contieneTredicesima,
        boolean contieneQuattordicesima,
        ProgressiviAnnui progressivi) {

    public CedolinoNormalizzato {
        voci = voci == null ? List.of() : List.copyOf(voci);
        ratei = ratei == null ? List.of() : List.copyOf(ratei);
        elementiRetributivi = elementiRetributivi == null ? ElementiRetributivi.VUOTI : elementiRetributivi;
        presenze = presenze == null ? Presenze.VUOTE : presenze;
        buoniPasto = buoniPasto == null ? BuoniPasto.ASSENTI : buoniPasto;
        progressivi = progressivi == null ? ProgressiviAnnui.VUOTI : progressivi;
    }

    public TipoMensilita tipo() {
        return competenza.tipo();
    }

    /** Voci di una categoria, nell'ordine di stampa. */
    public List<Voce> vociDi(CategoriaVoce categoria) {
        return voci.stream().filter(v -> v.categoria() == categoria).toList();
    }
}
