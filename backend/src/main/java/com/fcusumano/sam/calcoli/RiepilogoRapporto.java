package com.fcusumano.sam.calcoli;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Riepilogo derivato di un rapporto di lavoro. Ricalcolabile da zero dai cedolini normalizzati, dal rapporto e dal
 * CCNL: a parita' di input il contenuto e' identico (solo {@code calcolatoIl} cambia). I sotto-record non sono mai
 * null; i valori non determinabili sono null. Ogni valore stimato e' marcato come tale (flag o nota).
 *
 * @param calcolatoIl istante del ricalcolo
 * @param buoniPasto  true se almeno uno degli ultimi cedolini ordinari (3 per default) riporta buoni pasto
 * @param ratei       saldi dell'ultimo cedolino che riporta ratei, con stima del valore (lista vuota se non stampati)
 * @param mensilita   indicatori per cedolino, in ordine cronologico (scatto in busta, aumento fuori scatti)
 * @param liquidazione stima di quanto spetterebbe lasciando l'azienda alla data dell'ultimo cedolino (o alla
 *                     cessazione); {@link Liquidazione#NESSUNA} se il TFR e' gia' liquidato o non ci sono mensilita'
 */
public record RiepilogoRapporto(
        RapportoLavoroId rapportoId,
        OffsetDateTime calcolatoIl,
        Anzianita anzianita,
        Scatti scatti,
        Retribuzione retribuzione,
        boolean buoniPasto,
        Ratei ratei,
        Tfr tfr,
        List<IndicatoriMensilita> mensilita,
        Liquidazione liquidazione) {

    public RiepilogoRapporto {
        mensilita = mensilita == null ? List.of() : List.copyOf(mensilita);
        liquidazione = liquidazione == null ? Liquidazione.NESSUNA : liquidazione;
    }

    /**
     * Anzianita' aziendale in mesi inclusi, estremi compresi: inizio = mese della data di assunzione (se nota) o del
     * primo cedolino ordinario; fine = mese della data di cessazione (se nota) o dell'ultimo cedolino ordinario.
     * Tredicesime, quattordicesime e cedolini di fine rapporto non contano come mesi.
     * Es. Gennaio 2024 - Febbraio 2026 = 2 anni e 2 mesi; Gennaio 2024 - Dicembre 2025 = 2 anni.
     *
     * @param dal     null se non determinabile
     * @param al      mese finale considerato (ultimo cedolino ordinario se il rapporto e' in corso); null se non
     *                determinabile
     * @param inCorso true se non e' nota una data di cessazione
     */
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
     * Scatti di anzianita'.
     *
     * @param attuali         numero di scatti attuali: stampato sull'ultima mensilita' ordinaria (o del mese finale),
     *                        oppure stimato (dalle regole del CCNL, o numero stampato su un cedolino precedente); null
     *                        se non determinabile
     * @param massimo         massimo previsto dal CCNL; null se non noto
     * @param prossimoScatto  data prevista del prossimo scatto (stampata o da CCNL); null se non determinabile, se il
     *                        massimo e' raggiunto o se il rapporto e' cessato
     * @param fonteProssimo   CEDOLINI se stampato, CCNL se calcolato, NON_DISPONIBILE
     * @param fonteAttuali    CEDOLINI se stampato, CCNL se stimato dalle regole del CCNL, NON_DISPONIBILE
     * @param attualiStimati  true se {@code attuali} e' una stima (dalle regole del CCNL, o numero stampato su un
     *                        cedolino non recente)
     */
    public record Scatti(
            Integer attuali,
            Integer massimo,
            LocalDate prossimoScatto,
            FonteDato fonteProssimo,
            FonteDato fonteAttuali,
            boolean attualiStimati) {
    }

    /**
     * RAL = lordo mensile contrattuale dell'ultima mensilita' ordinaria x mensilita' del CCNL. Il lordo mensile e' il
     * totale degli elementi fissi stampato, altrimenti la somma di minimo + contingenza + superminimo + scatti + altri
     * elementi fissi, altrimenti la retribuzione base: voci variabili, straordinari, rimborsi e buoni pasto sono esclusi
     * per costruzione. Scomposizione come stampata; se il cedolino stampa solo la retribuzione base, e' valorizzata solo
     * {@code retribuzioneBase}.
     *
     * @param retribuzioneBase retribuzione base quando non scomposta; null altrimenti
     * @param stimata     true se calcolata con ipotesi (es. CCNL non indicato: 13 mensilita' assunte)
     * @param nota        spiegazione delle ipotesi o del motivo per cui la RAL non e' determinabile; null se nessuna
     * @param riferimento cedolino ordinario usato come riferimento; null se nessuno
     */
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
            CedolinoId riferimento) {
    }

    /**
     * Ratei residui dell'ultimo cedolino che li riporta.
     *
     * @param riferimento cedolino da cui sono letti i saldi; null se nessun cedolino riporta ratei
     */
    public record Ratei(CedolinoId riferimento, List<SaldoRateo> saldi) {

        public Ratei {
            saldi = saldi == null ? List.of() : List.copyOf(saldi);
        }
    }

    /**
     * Saldo di un rateo con la stima (sempre una stima) del valore economico se liquidato.
     *
     * @param valoreStimato saldo x paga giornaliera (giorni) o oraria (ore); null se non stimabile
     * @param nota          base usata per la stima (paga stampata o lordo mensile / divisore); null se non stimabile
     */
    public record SaldoRateo(TipoRateo tipo, UnitaMisura unita, BigDecimal saldo, BigDecimal valoreStimato, String nota) {
    }

    /**
     * TFR dall'ultimo cedolino che riporta dati TFR.
     *
     * @param totaleAccumulato    TFR in azienda + quote versate ai fondi pensione risultanti dai cedolini caricati;
     *                            0 se liquidato; null se non determinabile
     * @param quotaMensile        quota del mese dell'ultimo cedolino ordinario con dati TFR (stampata o stimata)
     * @param quotaInAzienda      TFR accumulato in azienda: fondo al 31/12 dell'anno precedente + rivalutazione - imposta
     *                            sostitutiva + quota dell'anno - quote a fondi e in busta dell'anno - anticipi dell'anno
     * @param quotaFondi          quote versate a fondi pensione risultanti dai cedolini caricati
     * @param quotaBusta          quote liquidate in busta (QuIR) risultanti dai cedolini caricati
     * @param anticipi            anticipi erogati risultanti dai cedolini caricati
     * @param stimaNetto          stima del netto della quota in azienda con tassazione separata parametrica (sempre
     *                            una stima); null se non determinabile o se il TFR e' liquidato
     * @param aliquotaStimata     aliquota usata per la stima
     * @param stimato             true se il totale e' stimato (dati mancanti ricostruiti, vedi nota)
     * @param riferimento         mese del cedolino da cui e' letto il TFR
     * @param nota                spiegazione delle ipotesi; null se nessuna
     * @param quotaMensileStimata true se la quota mensile non e' stampata ed e' stimata (retribuzione utile / 13,5)
     * @param liquidato           true se un cedolino di fine rapporto riporta il TFR liquidato
     * @param importoLiquidato    TFR liquidato lordo (somma dei cedolini di liquidazione); null se non liquidato
     * @param nettoLiquidato      TFR liquidato al netto della ritenuta stampata; null se non liquidato o senza ritenuta
     */
    public record Tfr(
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
     * Indicatori di un cedolino rispetto alla precedente mensilita' ordinaria (per i cedolini non ordinari sono false).
     *
     * @param scattoInBusta       true se aumenta il numero o l'importo degli scatti di anzianita'
     * @param aumentoFuoriScatti  true se il lordo contrattuale al netto degli scatti aumenta oltre la tolleranza (rinnovo,
     *                            superminimo, passaggio di livello); falso se il minimo cresce e il superminimo
     *                            assorbibile cala a parita' di totale
     * @param lordoContrattuale   lordo mensile contrattuale (elementi fissi) del cedolino; null se non stampato
     */
    public record IndicatoriMensilita(
            CedolinoId cedolinoId,
            boolean scattoInBusta,
            boolean aumentoFuoriScatti,
            BigDecimal lordoContrattuale) {
    }

    /**
     * Stima della liquidazione ("Se andassi via oggi"): TFR in azienda, ferie/permessi residui e ratei di tredicesima e
     * quattordicesima maturati e non pagati, con contributi e imposte stimati. Sempre una stima. Il preavviso non e'
     * considerato (lavorato).
     *
     * @param dataRiferimento           data della stima: cessazione se nota, altrimenti fine del mese dell'ultimo
     *                                  cedolino ordinario; null se la stima non si applica (vedi {@link #NESSUNA})
     * @param voci                      voci nell'ordine di presentazione; solo quelle con qualcosa da liquidare o con
     *                                  un importo non stimabile da segnalare
     * @param aliquotaContributi        aliquota dei contributi INPS a carico del dipendente applicata a ferie e ratei
     * @param aliquotaContributiStampata true se letta dal cedolino, false se da configurazione
     * @param aliquotaIrpefMarginale    aliquota IRPEF marginale stimata dalla RAL; null se la RAL non e' nota
     * @param aliquotaTfr               aliquota della tassazione separata del TFR; null se non determinabile
     * @param nota                      ipotesi generali della stima; null se la stima non si applica
     */
    public record Liquidazione(
            LocalDate dataRiferimento,
            List<VoceLiquidazione> voci,
            BigDecimal aliquotaContributi,
            boolean aliquotaContributiStampata,
            BigDecimal aliquotaIrpefMarginale,
            BigDecimal aliquotaTfr,
            String nota) {

        public static final Liquidazione NESSUNA = new Liquidazione(null, List.of(), null, false, null, null, null);

        public Liquidazione {
            voci = voci == null ? List.of() : List.copyOf(voci);
        }

        /** True se la stima si applica (TFR non ancora liquidato e almeno una mensilita' ordinaria). */
        public boolean presente() {
            return dataRiferimento != null;
        }
    }

    /**
     * Voce della liquidazione stimata. Netto = lordo - contributi - imposte. Gli importi sono null se non stimabili
     * (la nota spiega perche'); un lordo negativo e' un debito (es. ferie godute in anticipo).
     *
     * @param quantita  giorni/ore di ferie e permessi, mesi di rateo per tredicesima e quattordicesima; null per il TFR
     * @param unita     unita' della quantita' per ferie e permessi; null per TFR e ratei (mesi)
     * @param contributi contributi INPS a carico del dipendente (0 per il TFR)
     * @param imposte   IRPEF stimata (tassazione separata per il TFR, aliquota marginale per le altre voci)
     * @param nota      base del calcolo o motivo per cui non e' stimabile
     */
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
