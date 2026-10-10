package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Regole che stabiliscono la competenza di un cedolino a partire dal tipo dichiarato e dal periodo di retribuzione
 * stampato:
 * <ul>
 *   <li>FINE_RAPPORTO: cedolino di fine rapporto sul periodo stampato (il progressivo TFR si assegna dopo, in ordine
 *       cronologico dei periodi del rapporto: vedi {@link NumerazioneFineRapporto});</li>
 *   <li>ORDINARIA, TREDICESIMA e QUATTORDICESIMA con periodo successivo al mese di cessazione (stampata sul cedolino o
 *       nota per il rapporto): cedolino di fine rapporto sul periodo stampato, perche' ogni cedolino successivo alla
 *       cessazione e' un TFRnn. Non si applica se il cedolino stampa un'assunzione successiva alla cessazione
 *       (riassunzione presso la stessa azienda). Il mese di cessazione resta una mensilita';</li>
 *   <li>altrimenti TREDICESIMA e' il mese 13 dell'anno del periodo (anche se il periodo stampato e'
 *       "Dicembre ... AGG."), QUATTORDICESIMA il mese 14, ORDINARIA la mensilita' del periodo;</li>
 *   <li>AGGIUNTIVA restituisce la mensilita' ordinaria del periodo con cui il cedolino aggiuntivo confligge.</li>
 * </ul>
 * La classificazione di un cedolino successivo alla cessazione non cambia i suoi dati: una tredicesima pagata dopo la
 * cessazione resta un cedolino che contiene la tredicesima.
 */
public final class DeterminazioneCompetenza {

    private DeterminazioneCompetenza() {
    }

    /** Competenza determinata: mensilita' (definitiva) o fine rapporto (progressivo ancora da assegnare). */
    public sealed interface Esito permits Mensile, DiFineRapporto {
    }

    public record Mensile(Competenza.Mensilita competenza) implements Esito {
    }

    public record DiFineRapporto(YearMonth periodo) implements Esito {
    }

    /**
     * @param tipo                tipo dichiarato dall'estrazione
     * @param periodo             periodo di retribuzione stampato (obbligatorio)
     * @param assunzioneStampata  data di assunzione stampata sul cedolino, null se assente
     * @param cessazione          data di cessazione stampata sul cedolino o, in mancanza, quella nota per il rapporto
     */
    public static Esito determina(TipoDichiarato tipo, YearMonth periodo, LocalDate assunzioneStampata,
                                  LocalDate cessazione) {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(periodo, "periodo");
        var fineRapporto = new DiFineRapporto(periodo);
        boolean dopoCessazione = successivoAllaCessazione(periodo, assunzioneStampata, cessazione);
        return switch (tipo) {
            case FINE_RAPPORTO -> fineRapporto;
            case AGGIUNTIVA -> new Mensile(Competenza.Mensilita.ordinaria(periodo));
            case ORDINARIA -> dopoCessazione ? fineRapporto : new Mensile(Competenza.Mensilita.ordinaria(periodo));
            case TREDICESIMA -> dopoCessazione ? fineRapporto
                    : new Mensile(Competenza.Mensilita.tredicesima(periodo.getYear()));
            case QUATTORDICESIMA -> dopoCessazione ? fineRapporto
                    : new Mensile(Competenza.Mensilita.quattordicesima(periodo.getYear()));
        };
    }

    /**
     * True se il periodo e' successivo al mese di cessazione e il cedolino non documenta una riassunzione
     * (assunzione stampata successiva alla cessazione).
     */
    public static boolean successivoAllaCessazione(YearMonth periodo, LocalDate assunzioneStampata, LocalDate cessazione) {
        if (cessazione == null || !periodo.isAfter(YearMonth.from(cessazione))) {
            return false;
        }
        return assunzioneStampata == null || !assunzioneStampata.isAfter(cessazione);
    }
}
