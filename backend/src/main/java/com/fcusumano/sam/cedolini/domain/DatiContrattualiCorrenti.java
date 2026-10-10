package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Dati contrattuali del rapporto ricostruiti dai cedolini archiviati (dopo un'eliminazione): per ogni dato vale il
 * valore stampato sul cedolino piu' recente che lo riporta (ordine cronologico delle competenze).
 *
 * @param riferimento periodo di retribuzione del cedolino piu' recente
 */
public record DatiContrattualiCorrenti(
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        String codiceDipendente,
        String ccnlTesto,
        YearMonth riferimento) {

    private static final Comparator<CedolinoNormalizzato> DAL_PIU_RECENTE = Comparator
            .comparing(CedolinoNormalizzato::competenza, Competenza.ORDINE_CRONOLOGICO)
            .reversed();

    /** Dati ricostruiti; null se non ci sono cedolini. */
    public static DatiContrattualiCorrenti da(Collection<CedolinoNormalizzato> cedolini) {
        if (cedolini.isEmpty()) {
            return null;
        }
        var ordinati = cedolini.stream().sorted(DAL_PIU_RECENTE).toList();
        return new DatiContrattualiCorrenti(
                primo(ordinati, CedolinoNormalizzato::dataAssunzione),
                primo(ordinati, CedolinoNormalizzato::dataCessazione),
                primo(ordinati, CedolinoNormalizzato::qualifica),
                primo(ordinati, CedolinoNormalizzato::livello),
                primo(ordinati, CedolinoNormalizzato::codiceDipendente),
                primo(ordinati, CedolinoNormalizzato::ccnlTesto),
                ordinati.stream().map(CedolinoNormalizzato::periodoRetribuzione)
                        .max(Comparator.naturalOrder()).orElseThrow());
    }

    private static <T> T primo(List<CedolinoNormalizzato> ordinati, Function<CedolinoNormalizzato, T> campo) {
        return ordinati.stream().map(campo).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
