package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Letture annuali dei dati TFR stampati, comuni alle regole FONDO_TFR_IN_CALO e TFR_INCOERENTE.
 * <p>
 * Il fondo al 31/12 dell'anno precedente stampato sui cedolini dell'anno Y e' il fondo al 31/12/Y-1. Si usa il valore
 * dell'ultimo cedolino dell'anno che lo stampa (escluso il fine rapporto): e' il piu' aggiornato (alcuni software lo
 * aggiornano solo dopo la chiusura annuale).
 */
final class StoricoTfr {

    private final ContestoRapporto contesto;
    private final BigDecimal tolleranzaCalo;

    StoricoTfr(ContestoRapporto contesto, BigDecimal tolleranzaCalo) {
        this.contesto = contesto;
        this.tolleranzaCalo = tolleranzaCalo;
    }

    /** Valore stampato con il cedolino da cui proviene. */
    record ValoreStampato(BigDecimal valore, CedolinoNormalizzato cedolino) {
    }

    /** Calo del fondo al 31/12 dell'anno rispetto al 31/12 dell'anno precedente. */
    record CaloFondo(int anno, ValoreStampato precedente, ValoreStampato successivo) {

        BigDecimal differenza() {
            return precedente.valore().subtract(successivo.valore());
        }
    }

    /** Anni (di competenza) i cui cedolini stampano il fondo al 31/12 dell'anno precedente. */
    List<Integer> anniConFondo() {
        return contesto.cedolini().stream()
                .filter(c -> c.tipo() != TipoMensilita.FINE_RAPPORTO && c.tfr() != null
                        && c.tfr().fondo31DicembreAnnoPrecedente() != null)
                .map(c -> c.competenza().anno()).distinct().sorted().toList();
    }

    /** Fondo al 31/12/{@code anno - 1} come stampato sui cedolini di {@code anno} (ultimo valore stampato). */
    Optional<ValoreStampato> fondoStampatoNel(int anno) {
        return cedoliniTfr(anno).stream()
                .filter(c -> c.tfr().fondo31DicembreAnnoPrecedente() != null)
                .reduce((a, b) -> b)
                .map(c -> new ValoreStampato(c.tfr().fondo31DicembreAnnoPrecedente(), c));
    }

    /**
     * Calo del fondo tra il 31/12/{@code anno - 1} (stampato nell'anno) e il 31/12/{@code anno} (stampato nell'anno
     * successivo), solo se oltre la tolleranza, senza cause note nell'anno e con tutti i cedolini ordinari dell'anno
     * presenti (un mese mancante potrebbe nascondere un anticipo).
     */
    Optional<CaloFondo> caloSenzaCauseNote(int anno) {
        var precedente = fondoStampatoNel(anno);
        var successivo = fondoStampatoNel(anno + 1);
        if (precedente.isEmpty() || successivo.isEmpty()
                || !Attivita.inCalo(precedente.get().valore(), successivo.get().valore(), tolleranzaCalo)
                || haCauseDiRiduzione(anno) || !annoCompleto(anno)) {
            return Optional.empty();
        }
        return Optional.of(new CaloFondo(anno, precedente.get(), successivo.get()));
    }

    /** Anticipi, quote a fondi, liquidazioni, fine rapporto o cessazione nell'anno. */
    boolean haCauseDiRiduzione(int anno) {
        var cessazione = contesto.dataCessazione();
        if (cessazione != null && cessazione.getYear() == anno) {
            return true;
        }
        return contesto.dellAnno(anno).stream().anyMatch(c -> c.tipo() == TipoMensilita.FINE_RAPPORTO
                || c.tfr() != null && (Attivita.positivo(c.tfr().anticipi()) || Attivita.positivo(c.tfr().quotaFondi())
                || Attivita.positivo(c.tfr().liquidatoLordo())));
    }

    /** Tutti i mesi ordinari dell'anno (dal mese di assunzione se nell'anno) hanno il cedolino. */
    boolean annoCompleto(int anno) {
        var assunzione = contesto.dataAssunzione();
        var inizio = assunzione != null && assunzione.getYear() == anno ? YearMonth.from(assunzione) : YearMonth.of(anno, 1);
        for (var mese = inizio; mese.getYear() == anno; mese = mese.plusMonths(1)) {
            if (contesto.ordinario(mese).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Cedolini dell'anno con dati TFR, esclusi quelli di fine rapporto, in ordine cronologico. */
    List<CedolinoNormalizzato> cedoliniTfr(int anno) {
        return contesto.dellAnno(anno).stream()
                .filter(c -> c.tipo() != TipoMensilita.FINE_RAPPORTO && c.tfr() != null)
                .toList();
    }

    /**
     * Importo annuo di un campo che il cedolino puo' stampare come valore del mese o come progressivo (anticipi, quote
     * a fondi). Si legge sulle mensilita' ordinarie dell'anno:
     * <ul>
     *   <li>nessun valore positivo: zero;</li>
     *   <li>un solo valore positivo: quel valore (mese o progressivo coincidono);</li>
     *   <li>valori positivi su tutti i cedolini dal primo in poi, strettamente crescenti: progressivo, vale l'ultimo;</li>
     *   <li>se {@code costanteProgressivo}, valori positivi tutti uguali dal primo in poi: progressivo di un solo
     *       movimento (es. un anticipo), vale quel valore;</li>
     *   <li>altrimenti l'interpretazione e' ambigua: vuoto.</li>
     * </ul>
     * Un valore piu' alto stampato su tredicesima/quattordicesima rende la lettura ambigua.
     */
    Optional<BigDecimal> importoAnnuo(int anno, Function<DatiTfr, BigDecimal> campo, boolean costanteProgressivo) {
        var ordinari = cedoliniTfr(anno).stream().filter(c -> c.tipo() == TipoMensilita.ORDINARIA).toList();
        var valori = new ArrayList<BigDecimal>();
        boolean iniziato = false;
        for (var cedolino : ordinari) {
            var valore = campo.apply(cedolino.tfr());
            if (Attivita.positivo(valore)) {
                iniziato = true;
                valori.add(valore);
            } else if (iniziato) {
                valori.add(BigDecimal.ZERO);
            }
        }
        var massimoAltri = cedoliniTfr(anno).stream().filter(c -> c.tipo() != TipoMensilita.ORDINARIA)
                .map(c -> campo.apply(c.tfr())).filter(Attivita::positivo).max(BigDecimal::compareTo);
        var risultato = interpreta(valori, costanteProgressivo);
        if (risultato.isPresent() && massimoAltri.isPresent() && massimoAltri.get().compareTo(risultato.get()) > 0) {
            return Optional.empty();
        }
        return risultato;
    }

    private static Optional<BigDecimal> interpreta(List<BigDecimal> valori, boolean costanteProgressivo) {
        if (valori.isEmpty()) {
            return Optional.of(BigDecimal.ZERO);
        }
        long positivi = valori.stream().filter(Attivita::positivo).count();
        if (positivi == 1) {
            // Un solo cedolino con valore (eventualmente seguito da mesi a zero): mese e progressivo coincidono
            return Optional.of(valori.getFirst());
        }
        if (positivi != valori.size()) {
            return Optional.empty();
        }
        boolean crescenti = true;
        boolean costanti = true;
        for (int i = 1; i < valori.size(); i++) {
            int confronto = valori.get(i).compareTo(valori.get(i - 1));
            crescenti &= confronto > 0;
            costanti &= confronto == 0;
        }
        if (crescenti || (costanti && costanteProgressivo)) {
            return Optional.of(valori.getLast());
        }
        return Optional.empty();
    }
}
