package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.application.CedoliniRepository.AssegnazioneCompetenza;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto.Assegnazione;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto.Cedolino;
import com.fcusumano.sam.cedolini.domain.NomeFileCedolinoStrategy;
import com.fcusumano.sam.cedolini.domain.TipoDichiarato;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

/**
 * Mantiene coerente la classificazione dei cedolini di un rapporto con la sua data di cessazione: mensilita' successive
 * alla cessazione come cedolini di fine rapporto (e viceversa quando la cessazione sparisce), progressivi TFR01,
 * TFR02, ... in ordine cronologico del periodo stampato e nomi file conseguenti (regole in
 * {@link ClassificazioneRapporto}). Usata dopo ogni acquisizione ed eliminazione, dentro la transazione serializzata.
 */
@Component
class ClassificazioneCedolini {

    /** Id provvisorio del cedolino in acquisizione, non ancora salvato (gli id a DB partono da 1). */
    private static final long NUOVO = 0L;

    private final CedoliniRepository cedolini;
    private final NomeFileCedolinoStrategy nomeFile;

    ClassificazioneCedolini(CedoliniRepository cedolini, NomeFileCedolinoStrategy nomeFile) {
        this.cedolini = cedolini;
        this.nomeFile = nomeFile;
    }

    /**
     * Cedolino in acquisizione, con la competenza gia' determinata e la chiave di unicita' gia' verificata libera.
     *
     * @param competenza competenza determinata; per un cedolino di fine rapporto il progressivo e' provvisorio
     */
    record CedolinoInArrivo(TipoDichiarato dichiarato, YearMonth periodo, LocalDate assunzioneStampata,
                            Competenza competenza) {
    }

    /**
     * Esito della riclassificazione.
     *
     * @param competenzaInArrivo competenza definitiva del cedolino in acquisizione, null se non c'era
     * @param modificati         cedolini archiviati la cui competenza (o il cui progressivo) e' cambiata
     */
    record Esito(Competenza competenzaInArrivo, int modificati) {
    }

    /**
     * Riclassifica i cedolini archiviati del rapporto e aggiorna competenze e nomi file di quelli che cambiano.
     *
     * @param cessazione data di cessazione del rapporto, null se in corso o non nota
     * @param inArrivo   cedolino non ancora salvato da considerare (la sua classificazione non cambia, ma il progressivo
     *                   TFR si), null se nessuno
     */
    Esito riclassifica(WorkspaceId workspace, RapportoLavoro rapporto, LocalDate cessazione, CedolinoInArrivo inArrivo) {
        var elenco = new ArrayList<>(cedolini.classificabili(workspace, rapporto.id()));
        if (inArrivo != null) {
            elenco.add(new Cedolino(NUOVO, inArrivo.dichiarato(), inArrivo.periodo(), inArrivo.assunzioneStampata(),
                    inArrivo.competenza(), true));
        }
        Competenza competenzaInArrivo = inArrivo == null ? null : inArrivo.competenza();
        var aggiornamenti = new ArrayList<AssegnazioneCompetenza>();
        for (Assegnazione a : ClassificazioneRapporto.classifica(elenco, cessazione)) {
            if (a.id() == NUOVO) {
                competenzaInArrivo = a.competenza();
            } else {
                aggiornamenti.add(new AssegnazioneCompetenza(new CedolinoId(a.id()), a.competenza(),
                        nomeFile.nomeFile(a.competenza(), rapporto.aziendaId(), rapporto.personaId())));
            }
        }
        if (!aggiornamenti.isEmpty()) {
            cedolini.assegnaCompetenze(workspace, aggiornamenti);
        }
        return new Esito(competenzaInArrivo, aggiornamenti.size());
    }
}
