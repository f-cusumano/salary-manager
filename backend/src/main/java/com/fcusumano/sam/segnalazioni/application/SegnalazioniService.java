package com.fcusumano.sam.segnalazioni.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.domain.ContestoRapporto;
import com.fcusumano.sam.segnalazioni.domain.ContestoRapporto.FileInErrore;
import com.fcusumano.sam.segnalazioni.domain.MotoreSegnalazioni;
import com.fcusumano.sam.segnalazioni.domain.RegolaSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servizio segnalazioni: ricalcolo da zero con le regole registrate (tutti i bean {@link RegolaSegnalazione}) e
 * letture per la UI. Il ricalcolo partecipa alla transazione di chi pubblica l'evento (acquisizione o eliminazione di
 * un cedolino, cambio CCNL, file in errore associati a una mensilita'): tutto o niente.
 */
@Service
class SegnalazioniService implements SegnalazioniRapporto {

    private static final Logger log = LoggerFactory.getLogger(SegnalazioniService.class);

    /**
     * Tipi che alimentano la lista delle mensilita' mancanti. Le mensilita' con un file in errore associato non ne fanno
     * parte: la loro riga nella lista mensilita' viene dal modulo cedolini.
     */
    private static final Set<TipoSegnalazione> TIPI_MENSILITA_MANCANTI = EnumSet.of(
            TipoSegnalazione.MENSILITA_MANCANTE,
            TipoSegnalazione.TREDICESIMA_MANCANTE,
            TipoSegnalazione.QUATTORDICESIMA_MANCANTE);

    private final ConsultazioneAnagrafica anagrafica;
    private final CatalogoCcnl catalogoCcnl;
    private final ArchivioCedolini archivio;
    private final CaricamentiInErrore caricamentiInErrore;
    private final SegnalazioniRepository repository;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final Clock clock;
    private final MotoreSegnalazioni motore;

    SegnalazioniService(ConsultazioneAnagrafica anagrafica, CatalogoCcnl catalogoCcnl, ArchivioCedolini archivio,
                        CaricamentiInErrore caricamentiInErrore, SegnalazioniRepository repository,
                        CurrentWorkspaceProvider workspaceProvider, Clock clock, List<RegolaSegnalazione> regole) {
        this.anagrafica = anagrafica;
        this.catalogoCcnl = catalogoCcnl;
        this.archivio = archivio;
        this.caricamentiInErrore = caricamentiInErrore;
        this.repository = repository;
        this.workspaceProvider = workspaceProvider;
        this.clock = clock;
        this.motore = new MotoreSegnalazioni(regole, SegnalazioniService::regolaInErrore);
        log.info("Regole di segnalazione registrate: {}", regole.stream().map(RegolaSegnalazione::codice).sorted().toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Segnalazione> segnalazioni(RapportoLavoroId rapportoId) {
        return repository.perRapporto(workspaceProvider.corrente(), rapportoId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean haSegnalazioni(RapportoLavoroId rapportoId) {
        return repository.esistono(workspaceProvider.corrente(), rapportoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competenza.Mensilita> mensilitaMancanti(RapportoLavoroId rapportoId) {
        return repository.competenze(workspaceProvider.corrente(), rapportoId, TIPI_MENSILITA_MANCANTI);
    }

    @Override
    @Transactional
    public void ricalcola(RapportoLavoroId rapportoId) {
        var workspace = workspaceProvider.corrente();
        var rapporto = anagrafica.trovaRapporto(rapportoId);
        if (rapporto.isEmpty()) {
            repository.sostituisci(workspace, rapportoId, List.of());
            return;
        }
        var ccnl = rapporto.get().ccnlCodice() == null ? null
                : catalogoCcnl.trova(rapporto.get().ccnlCodice()).orElse(null);
        var contesto = new ContestoRapporto(rapporto.get(), ccnl, archivio.normalizzatiDiRapporto(rapportoId),
                LocalDate.now(clock), mensilitaInErrore(rapportoId));
        var segnalazioni = motore.valuta(contesto);
        repository.sostituisci(workspace, rapportoId, segnalazioni);
        log.debug("Segnalazioni ricalcolate per il rapporto {}: {}", rapportoId.valore(), segnalazioni.size());
    }

    /**
     * Mensilita' del rapporto con un file in errore associato dall'utente, con il motivo (null se non registrato) e la
     * presenza del PDF conservato.
     */
    private Map<Competenza.Mensilita, FileInErrore> mensilitaInErrore(RapportoLavoroId rapportoId) {
        var mensilita = new HashMap<Competenza.Mensilita, FileInErrore>();
        for (var file : caricamentiInErrore.associatiARapporto(rapportoId)) {
            if (file.attribuzione() != null) {
                mensilita.put(file.attribuzione().competenza(), new FileInErrore(file.motivo(), file.pdfDisponibile()));
            }
        }
        return mensilita;
    }

    /**
     * Regola saltata per un errore. Il messaggio dell'eccezione puo' contenere date o importi dei cedolini: a livello
     * ERROR solo il codice della regola e il tipo di errore, il dettaglio completo solo a DEBUG.
     */
    static void regolaInErrore(RegolaSegnalazione regola, RuntimeException errore) {
        log.error("Regola di segnalazione {} non valutata per un errore interno: {}", regola.codice(),
                errore.getClass().getName());
        log.debug("Dettaglio dell'errore della regola {}", regola.codice(), errore);
    }
}
