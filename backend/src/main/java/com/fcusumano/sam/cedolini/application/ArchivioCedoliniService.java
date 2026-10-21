package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archivio dei cedolini: letture per UI, calcoli e segnalazioni (partecipano alla transazione del chiamante, anche
 * quella di acquisizione) ed eliminazione.
 */
@Service
class ArchivioCedoliniService implements ArchivioCedolini {

    private static final Logger log = LoggerFactory.getLogger(ArchivioCedoliniService.class);

    private static final Comparator<CedolinoSintesi> SINTESI_CRONOLOGICA =
            Comparator.comparing(CedolinoSintesi::competenza, Competenza.ORDINE_CRONOLOGICO);

    private final CedoliniRepository cedolini;
    private final ConsultazioneAnagrafica consultazione;
    private final CensimentoAnagrafica censimento;
    private final RicostruzioneRapporto ricostruzione;
    private final ScrittureSerializzate scritture;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final ApplicationEventPublisher eventi;

    ArchivioCedoliniService(CedoliniRepository cedolini, ConsultazioneAnagrafica consultazione,
                            CensimentoAnagrafica censimento, RicostruzioneRapporto ricostruzione,
                            ScrittureSerializzate scritture,
                            CurrentWorkspaceProvider workspaceProvider, ApplicationEventPublisher eventi) {
        this.cedolini = cedolini;
        this.consultazione = consultazione;
        this.censimento = censimento;
        this.ricostruzione = ricostruzione;
        this.scritture = scritture;
        this.workspaceProvider = workspaceProvider;
        this.eventi = eventi;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CedolinoSintesi> sintesiDiRapporto(RapportoLavoroId rapportoId) {
        return cedolini.sintesi(workspaceProvider.corrente(), rapportoId).stream()
                .sorted(SINTESI_CRONOLOGICA.reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CedolinoNormalizzato> normalizzatiDiRapporto(RapportoLavoroId rapportoId) {
        return consultazione.trovaRapporto(rapportoId)
                .map(rapporto -> normalizzati(workspaceProvider.corrente(), rapporto))
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CedolinoDettaglio> dettaglio(CedolinoId cedolinoId) {
        var ws = workspaceProvider.corrente();
        return cedolini.trova(ws, cedolinoId)
                .flatMap(c -> consultazione.trovaRapporto(c.rapportoId()))
                .flatMap(r -> cedolini.dettaglio(ws, cedolinoId, r.personaId(), r.aziendaId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PdfCedolino> pdf(CedolinoId cedolinoId) {
        return cedolini.pdf(workspaceProvider.corrente(), cedolinoId);
    }

    /**
     * Elimina il cedolino in una transazione propria, serializzata con le acquisizioni del workspace: cancellazione
     * (figlie e PDF in cascata), dati contrattuali del rapporto ricostruiti dai cedolini rimasti, riclassificazione dei
     * cedolini rimasti rispetto alla nuova cessazione con rinumerazione dei progressivi TFR e dei nomi file, estremi del
     * rapporto, evento {@link CedolinoEliminato}. Lo stato finale e' quello che si avrebbe caricando da zero i soli
     * cedolini rimasti. Se non restano cedolini: eliminazione del rapporto e delle persona/azienda orfane.
     */
    @Override
    public void elimina(CedolinoId cedolinoId) {
        var ws = workspaceProvider.corrente();
        scritture.inTransazione(ws, stato -> {
            var cedolino = cedolini.trova(ws, cedolinoId)
                    .orElseThrow(() -> new RisorsaNonTrovataException("Cedolino", cedolinoId.valore()));
            var rapporto = consultazione.trovaRapporto(cedolino.rapportoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Rapporto del cedolino " + cedolinoId.valore() + " non trovato"));
            cedolini.elimina(ws, cedolinoId);
            var rimasti = normalizzati(ws, rapporto);
            if (rimasti.isEmpty()) {
                eventi.publishEvent(new CedolinoEliminato(cedolinoId, rapporto.id(), cedolino.competenza()));
                censimento.eliminaRapporto(rapporto.id());
            } else {
                ricostruzione.ricostruisci(ws, rapporto, rimasti);
                eventi.publishEvent(new CedolinoEliminato(cedolinoId, rapporto.id(), cedolino.competenza()));
            }
            log.info("Eliminato cedolino {} (rapporto {}, cedolini rimasti {})", cedolinoId.valore(),
                    rapporto.id().valore(), rimasti.size());
            return null;
        });
    }

    private List<CedolinoNormalizzato> normalizzati(WorkspaceId ws, RapportoLavoro rapporto) {
        return ricostruzione.normalizzati(ws, rapporto);
    }
}
