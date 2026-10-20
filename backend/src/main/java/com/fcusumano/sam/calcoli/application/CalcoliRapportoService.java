package com.fcusumano.sam.calcoli.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoroDettaglio;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.calcoli.Preavviso;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.calcoli.domain.CalcolatoreRiepilogo;
import com.fcusumano.sam.calcoli.domain.CalcoloPreavviso;
import com.fcusumano.sam.calcoli.domain.ContestoRapporto;
import com.fcusumano.sam.calcoli.domain.ParametriCalcolo;
import com.fcusumano.sam.calcoli.domain.ProiezioneTfr;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.RegolaPreavviso;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ricalcolo da zero dei dati derivati di un rapporto: legge rapporto (anagrafica), CCNL (catalogo) e cedolini
 * normalizzati (archivio), calcola il riepilogo nel dominio e lo sostituisce a quello salvato. Invocato in modo
 * sincrono dagli eventi, partecipa alla transazione di chi li pubblica. Il riepilogo salvato e' deterministico; in
 * lettura il TFR dei rapporti attuali e' proiettato al mese corrente ({@link ProiezioneTfr}).
 */
@Service
class CalcoliRapportoService implements CalcoliRapporto {

    private static final Logger log = LoggerFactory.getLogger(CalcoliRapportoService.class);

    private final ConsultazioneAnagrafica anagrafica;
    private final ArchivioCedolini archivio;
    private final CatalogoCcnl catalogo;
    private final RiepiloghiRepository repository;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final CalcolatoreRiepilogo calcolatore;
    private final ParametriCalcolo parametri;
    private final Clock clock;

    CalcoliRapportoService(ConsultazioneAnagrafica anagrafica, ArchivioCedolini archivio, CatalogoCcnl catalogo,
                           RiepiloghiRepository repository, CurrentWorkspaceProvider workspaceProvider,
                           CalcolatoreRiepilogo calcolatore, ParametriCalcolo parametri, Clock clock) {
        this.anagrafica = anagrafica;
        this.archivio = archivio;
        this.catalogo = catalogo;
        this.repository = repository;
        this.workspaceProvider = workspaceProvider;
        this.calcolatore = calcolatore;
        this.parametri = parametri;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiepilogoRapporto> riepilogo(RapportoLavoroId rapportoId) {
        return repository.trova(workspaceProvider.corrente(), rapportoId).map(this::conTfrAdOggi);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Preavviso> preavviso(RapportoLavoroId rapportoId) {
        var rapporto = anagrafica.dettaglioRapporto(rapportoId)
                .filter(d -> d.periodo().attuale() && d.rapporto().dataCessazione() == null)
                .map(RapportoLavoroDettaglio::rapporto);
        var riepilogo = repository.trova(workspaceProvider.corrente(), rapportoId)
                .filter(r -> r.anzianita() == null || r.anzianita().inCorso())
                .filter(r -> !r.tfr().liquidato());
        if (rapporto.isEmpty() || riepilogo.isEmpty()) {
            return Optional.empty();
        }
        var r = rapporto.get();
        var ccnl = Optional.ofNullable(r.ccnlCodice()).flatMap(catalogo::trova).orElse(null);
        var regole = ccnl == null ? List.<RegolaPreavviso>of() : catalogo.preavviso(ccnl.codice());
        return Optional.of(CalcoloPreavviso.calcola(ccnl, r.livello(), riepilogo.get().anzianita(),
                riepilogo.get().retribuzione(), regole));
    }

    /** Il riepilogo con il TFR proiettato al mese corrente, se il rapporto e' attuale (in corso, ultimo della persona). */
    private RiepilogoRapporto conTfrAdOggi(RiepilogoRapporto r) {
        var attuale = anagrafica.dettaglioRapporto(r.rapportoId())
                .filter(d -> d.periodo().attuale() && d.rapporto().dataCessazione() == null)
                .isPresent();
        if (!attuale) {
            return r;
        }
        var tfr = ProiezioneTfr.proietta(r.tfr(), YearMonth.now(clock), parametri);
        return tfr == r.tfr() ? r : new RiepilogoRapporto(r.rapportoId(), r.calcolatoIl(), r.anzianita(), r.scatti(),
                r.retribuzione(), r.buoniPasto(), r.ratei(), tfr, r.mensilita(), r.liquidazione());
    }

    @Override
    @Transactional
    public void ricalcola(RapportoLavoroId rapportoId) {
        var workspace = workspaceProvider.corrente();
        var rapporto = anagrafica.trovaRapporto(rapportoId);
        var cedolini = rapporto.map(r -> archivio.normalizzatiDiRapporto(rapportoId)).orElse(List.of());
        if (rapporto.isEmpty() || cedolini.isEmpty()) {
            repository.elimina(workspace, rapportoId);
            log.debug("Rapporto {} senza cedolini: dati derivati rimossi", rapportoId.valore());
            return;
        }
        var r = rapporto.get();
        var ccnl = Optional.ofNullable(r.ccnlCodice()).flatMap(catalogo::trova).orElse(null);
        var contesto = new ContestoRapporto(rapportoId, r.dataAssunzione(), r.dataCessazione(), ccnl);
        var riepilogo = calcolatore.calcola(contesto, cedolini, OffsetDateTime.now(clock));
        repository.sostituisci(workspace, riepilogo);
        log.debug("Riepilogo del rapporto {} ricalcolato da {} cedolini", rapportoId.valore(), cedolini.size());
    }
}
