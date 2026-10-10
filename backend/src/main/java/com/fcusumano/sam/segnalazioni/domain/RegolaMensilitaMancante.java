package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MENSILITA_MANCANTE (anomalia): mesi senza cedolino ORDINARIA tra il primo e l'ultimo cedolino ordinario del rapporto.
 * Se la data di cessazione e' nota ed e' successiva all'ultimo cedolino ordinario, l'intervallo si estende fino al mese
 * di cessazione (mai oltre il mese precedente a oggi, perche' il cedolino del mese in corso puo' non essere ancora
 * emesso). Una segnalazione per mese, con la competenza valorizzata: alimenta la lista delle mensilita' mancanti.
 * <p>
 * Esclusioni (casi in cui il mese non e' davvero dovuto):
 * <ul>
 *   <li>mese coperto da un cedolino di fine rapporto con lo stesso periodo di retribuzione;</li>
 *   <li>mese compreso tra una cessazione e una successiva riassunzione stampate sui cedolini (rapporto interrotto);</li>
 *   <li>prima del primo cedolino ordinario non si segnala nulla: i cedolini precedenti possono non essere stati
 *       caricati.</li>
 * </ul>
 */
public final class RegolaMensilitaMancante implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.MENSILITA_MANCANTE.name();

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var ordinari = contesto.ordinari();
        if (ordinari.isEmpty()) {
            return List.of();
        }
        var primo = ContestoRapporto.periodo(ordinari.getFirst());
        var ultimo = ContestoRapporto.periodo(ordinari.getLast());
        var fine = ultimo;
        var cessazione = contesto.dataCessazione();
        var ultimoEmettibile = YearMonth.from(contesto.oggi()).minusMonths(1);
        boolean estesoACessazione = false;
        if (cessazione != null && YearMonth.from(cessazione).isAfter(ultimo)) {
            var meseCessazione = YearMonth.from(cessazione);
            fine = meseCessazione.isAfter(ultimoEmettibile) ? ultimoEmettibile : meseCessazione;
            estesoACessazione = fine.isAfter(ultimo);
        }

        var coperti = new HashSet<YearMonth>();
        contesto.cedolini().stream()
                .filter(c -> c.tipo() == TipoMensilita.ORDINARIA || c.tipo() == TipoMensilita.FINE_RAPPORTO)
                .map(ContestoRapporto::periodo)
                .forEach(coperti::add);
        var interruzioni = interruzioni(contesto);

        var risultato = new ArrayList<SegnalazioneRilevata>();
        for (var mese = primo; !mese.isAfter(fine); mese = mese.plusMonths(1)) {
            if (coperti.contains(mese) || interrotto(mese, interruzioni)) {
                continue;
            }
            risultato.add(segnalazione(contesto, mese, primo, ultimo, estesoACessazione ? cessazione : null));
        }
        return risultato;
    }

    private SegnalazioneRilevata segnalazione(ContestoRapporto contesto, YearMonth mese, YearMonth primo,
                                              YearMonth ultimo, LocalDate cessazione) {
        var descrizione = new StringBuilder("Non risulta il cedolino di ").append(Formato.mese(mese)).append(". ");
        descrizione.append("I cedolini ordinari del rapporto vanno da ").append(Formato.mese(primo))
                .append(" a ").append(Formato.mese(ultimo));
        if (cessazione != null) {
            descrizione.append(" e la cessazione risulta il ").append(Formato.data(cessazione));
        }
        descrizione.append(": ogni mese del periodo dovrebbe avere il proprio cedolino.");

        var evidenze = new ArrayList<Evidenza>();
        precedente(contesto, mese).ifPresent(c -> evidenze.add(Evidenze.cedolino("Cedolino precedente", c)));
        successivo(contesto, mese).ifPresent(c -> evidenze.add(Evidenze.cedolino("Cedolino successivo", c)));
        if (cessazione != null) {
            evidenze.add(Evidenze.data("Data di cessazione", cessazione));
        }
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.MENSILITA_MANCANTE, Gravita.ANOMALIA, null,
                Competenza.Mensilita.ordinaria(mese), "Mensilità mancante: " + Formato.mese(mese),
                descrizione.toString(), evidenze);
    }

    private static Optional<CedolinoNormalizzato> precedente(ContestoRapporto contesto, YearMonth mese) {
        return contesto.ordinari().stream().filter(c -> ContestoRapporto.periodo(c).isBefore(mese))
                .reduce((a, b) -> b);
    }

    private static Optional<CedolinoNormalizzato> successivo(ContestoRapporto contesto, YearMonth mese) {
        return contesto.ordinari().stream().filter(c -> ContestoRapporto.periodo(c).isAfter(mese)).findFirst();
    }

    /** Intervalli (cessazione, riassunzione) stampati sui cedolini o sul rapporto in cui il rapporto era interrotto. */
    private static List<Interruzione> interruzioni(ContestoRapporto contesto) {
        Set<LocalDate> cessazioni = new HashSet<>();
        Set<LocalDate> assunzioni = new HashSet<>();
        Stream.concat(contesto.cedolini().stream().map(CedolinoNormalizzato::dataCessazione),
                Stream.of(contesto.dataCessazione())).filter(Objects::nonNull).forEach(cessazioni::add);
        Stream.concat(contesto.cedolini().stream().map(CedolinoNormalizzato::dataAssunzione),
                Stream.of(contesto.dataAssunzione())).filter(Objects::nonNull).forEach(assunzioni::add);
        var risultato = new ArrayList<Interruzione>();
        for (var cessazione : cessazioni) {
            assunzioni.stream().filter(a -> a.isAfter(cessazione)).min(LocalDate::compareTo)
                    .ifPresent(a -> risultato.add(new Interruzione(cessazione, a)));
        }
        return risultato;
    }

    /** Il mese cade interamente dopo una cessazione e prima della successiva riassunzione. */
    private static boolean interrotto(YearMonth mese, List<Interruzione> interruzioni) {
        return interruzioni.stream().anyMatch(i -> i.cessazione().isBefore(mese.atDay(1))
                && i.riassunzione().isAfter(mese.atEndOfMonth()));
    }

    private record Interruzione(LocalDate cessazione, LocalDate riassunzione) {
    }
}
