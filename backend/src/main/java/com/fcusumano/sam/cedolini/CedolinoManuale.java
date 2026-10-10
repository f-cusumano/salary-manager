package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cedolino inserito a mano per un file in errore: stessi campi e percorsi dello schema di lettura
 * ({@code prompts/cedolino-schema-v1.json}) con valori tipizzati. Un campo null e' un dato non stampato; le liste non
 * sono mai null. Il cedolino diventa il JSON dello schema e segue lo stesso percorso dei lettori (normalizzazione,
 * controlli, censimento, competenza, duplicati).
 *
 * @param retribuzione            {@link ElementiRetributivi#superminimoAssorbibile()} null = non indicato
 * @param voci                    voci nell'ordine di stampa; la categoria e' obbligatoria
 * @param tfr                     null se il cedolino non riporta dati TFR
 * @param contieneTredicesima     forzato a true per il tipo TREDICESIMA
 * @param contieneQuattordicesima forzato a true per il tipo QUATTORDICESIMA
 */
public record CedolinoManuale(
        Dipendente dipendente,
        DatoreLavoro datoreLavoro,
        DatiContrattuali datiContrattuali,
        Periodo periodo,
        Totali totali,
        ElementiRetributivi retribuzione,
        List<Voce> voci,
        List<Rateo> ratei,
        DatiTfr tfr,
        Presenze presenze,
        Scatti scatti,
        BuoniPasto buoniPasto,
        ProgressiviAnnui progressivi,
        boolean contieneTredicesima,
        boolean contieneQuattordicesima) {

    public CedolinoManuale {
        voci = copia(voci);
        ratei = copia(ratei);
    }

    /** Copia immutabile; null diventa vuota (gli elementi null restano, la validazione li segnala). */
    private static <T> List<T> copia(List<T> lista) {
        return lista == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lista));
    }

    public record Dipendente(String codiceFiscale, String nome, String cognome, LocalDate dataNascita,
                             String codiceDipendente) {
    }

    public record DatoreLavoro(String codiceFiscale, String partitaIva, String posizioneInps, String posizioneInail,
                               String ragioneSociale, String indirizzo, String codiceAzienda) {
    }

    public record DatiContrattuali(LocalDate dataAssunzione, LocalDate dataCessazione, String qualifica,
                                   String livello, String ccnl, String codiceCnel) {
    }

    /**
     * @param mese          mese 1..12 del periodo di retribuzione stampato (tredicesima e quattordicesima si indicano
     *                      con il tipo)
     * @param meseVariabili mese delle voci variabili, se stampato e diverso dal periodo
     */
    public record Periodo(Integer anno, Integer mese, TipoMensilita tipo, LocalDate dataElaborazione,
                          YearMonth meseVariabili) {
    }

    public record Totali(BigDecimal lordo, BigDecimal totaleCompetenze, BigDecimal totaleTrattenute,
                         BigDecimal arrotondamento, BigDecimal netto) {
    }

    public record Scatti(Integer numeroScatti, LocalDate dataProssimoScatto) {
    }
}
