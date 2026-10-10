package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Input delle regole: tutto cio' che serve per valutare un rapporto, con alcune letture di comodo sui cedolini.
 *
 * @param rapporto rapporto di lavoro (date contrattuali, CCNL)
 * @param ccnl     null se il CCNL non e' determinato
 * @param cedolini cedolini del rapporto; vengono sempre riordinati in ordine cronologico crescente di competenza
 *                 ({@link Competenza#ORDINE_CRONOLOGICO})
 * @param oggi     data di riferimento (da Clock, per test deterministici)
 * @param mensilitaInErrore mensilita' a cui l'utente ha associato un file caricato con errore, con il motivo
 *                          dell'errore (null se non registrato); in ordine cronologico. Una mensilita' puo' avere
 *                          anche il cedolino, archiviato nel frattempo da un altro file
 */
public record ContestoRapporto(RapportoLavoro rapporto, Ccnl ccnl, List<CedolinoNormalizzato> cedolini, LocalDate oggi,
                               Map<Competenza.Mensilita, MotivoErrore> mensilitaInErrore) {

    private static final Comparator<CedolinoNormalizzato> CRONOLOGICO =
            Comparator.comparing(CedolinoNormalizzato::competenza, Competenza.ORDINE_CRONOLOGICO);

    public ContestoRapporto {
        Objects.requireNonNull(rapporto, "rapporto");
        Objects.requireNonNull(oggi, "oggi");
        cedolini = cedolini == null ? List.of() : cedolini.stream().sorted(CRONOLOGICO).toList();
        // TreeMap: ordine cronologico e motivi null ammessi (Map.copyOf non li accetta)
        var ordinate = new TreeMap<Competenza.Mensilita, MotivoErrore>(Competenza.ORDINE_CRONOLOGICO);
        if (mensilitaInErrore != null) {
            ordinate.putAll(mensilitaInErrore);
        }
        mensilitaInErrore = Collections.unmodifiableMap(ordinate);
    }

    /** Contesto senza mensilita' con un file in errore associato. */
    public ContestoRapporto(RapportoLavoro rapporto, Ccnl ccnl, List<CedolinoNormalizzato> cedolini, LocalDate oggi) {
        this(rapporto, ccnl, cedolini, oggi, Map.of());
    }

    /**
     * Periodo di retribuzione del cedolino. Se non valorizzato si ricava dalla competenza: il mese stesso per le
     * mensilita' ordinarie, dicembre per la tredicesima, luglio per la quattordicesima (come nell'ordinamento).
     */
    public static YearMonth periodo(CedolinoNormalizzato cedolino) {
        if (cedolino.periodoRetribuzione() != null) {
            return cedolino.periodoRetribuzione();
        }
        return switch (cedolino.competenza()) {
            case Competenza.Mensilita m when m.mese() == 13 -> YearMonth.of(m.anno(), 12);
            case Competenza.Mensilita m when m.mese() == 14 -> YearMonth.of(m.anno(), 7);
            case Competenza.Mensilita m -> m.comeYearMonth();
            case Competenza.FineRapporto f -> f.periodoRetribuzione();
        };
    }

    /** Mensilita' ordinarie (mesi 1..12) in ordine cronologico. */
    public List<CedolinoNormalizzato> ordinari() {
        return diTipo(TipoMensilita.ORDINARIA);
    }

    public List<CedolinoNormalizzato> diTipo(TipoMensilita tipo) {
        return cedolini.stream().filter(c -> c.tipo() == tipo).toList();
    }

    /** Cedolini con competenza nell'anno indicato (per i cedolini di fine rapporto: anno del periodo di retribuzione). */
    public List<CedolinoNormalizzato> dellAnno(int anno) {
        return cedolini.stream().filter(c -> c.competenza().anno() == anno).toList();
    }

    /** Cedolino ordinario del mese indicato. */
    public Optional<CedolinoNormalizzato> ordinario(YearMonth mese) {
        return ordinari().stream().filter(c -> periodo(c).equals(mese)).findFirst();
    }

    /** True se esiste il cedolino con competenza anno + mese (es. 13 = tredicesima). */
    public boolean esisteCompetenza(int anno, int mese) {
        var cercata = new Competenza.Mensilita(anno, mese);
        return cedolini.stream().anyMatch(c -> cercata.equals(c.competenza()));
    }

    /**
     * True se l'utente ha associato un file caricato con errore alla competenza anno + mese (es. 13 = tredicesima):
     * il cedolino e' stato caricato, quindi la mensilita' non e' mancante (la segnala CEDOLINO_IN_ERRORE).
     */
    public boolean inErrore(int anno, int mese) {
        return mensilitaInErrore.containsKey(new Competenza.Mensilita(anno, mese));
    }

    /** Primo cedolino (di qualunque tipo) con periodo di retribuzione successivo al mese indicato. */
    public Optional<CedolinoNormalizzato> primoDopo(YearMonth mese) {
        return cedolini.stream().filter(c -> periodo(c).isAfter(mese)).findFirst();
    }

    /** True se esiste una mensilita' ordinaria successiva al mese indicato (il rapporto e' proseguito). */
    public boolean ordinariDopo(YearMonth mese) {
        return ordinari().stream().anyMatch(c -> periodo(c).isAfter(mese));
    }

    /** Cedolino per id, se appartiene al rapporto. */
    public Optional<CedolinoNormalizzato> cedolino(CedolinoId id) {
        return cedolini.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public LocalDate dataAssunzione() {
        return rapporto.dataAssunzione();
    }

    public LocalDate dataCessazione() {
        return rapporto.dataCessazione();
    }
}
