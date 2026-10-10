package com.fcusumano.sam.anagrafica.application;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.AziendaRiconosciuta;
import com.fcusumano.sam.anagrafica.AziendaSelezionabile;
import com.fcusumano.sam.anagrafica.CcnlRapportoModificato;
import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.DatiPersona;
import com.fcusumano.sam.anagrafica.DatiRapporto;
import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroDettaglio;
import com.fcusumano.sam.anagrafica.RapportoLavoroEliminato;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.anagrafica.domain.AbbinamentoAzienda;
import com.fcusumano.sam.anagrafica.domain.AggiornamentoAzienda;
import com.fcusumano.sam.anagrafica.domain.AggiornamentoRapporto;
import com.fcusumano.sam.anagrafica.domain.CodiceFiscalePersona;
import com.fcusumano.sam.anagrafica.domain.Etichette;
import com.fcusumano.sam.anagrafica.domain.NomiPropri;
import com.fcusumano.sam.anagrafica.domain.PeriodiRapporti;
import com.fcusumano.sam.anagrafica.domain.ProfiloAzienda;
import com.fcusumano.sam.anagrafica.domain.RagioneSociale;
import com.fcusumano.sam.anagrafica.domain.StatoRapporto;
import com.fcusumano.sam.anagrafica.domain.Testi;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servizio applicativo dell'anagrafica: censimento da cedolino, consultazione e modifiche dell'utente.
 * Tutte le operazioni lavorano sul workspace corrente e partecipano alla transazione del chiamante.
 * Nei log compaiono solo id: mai codici fiscali, nomi o ragioni sociali.
 */
@Service
@Transactional
class AnagraficaService implements CensimentoAnagrafica, ConsultazioneAnagrafica, GestioneRapporti {

    private static final Logger log = LoggerFactory.getLogger(AnagraficaService.class);

    /** Lunghezza massima di nome e cognome (colonne {@code persona.nome} e {@code persona.cognome}). */
    private static final int LUNGHEZZA_NOME = 100;
    /** Lunghezza massima delle note del rapporto. */
    private static final int LUNGHEZZA_NOTE = 4000;

    private static final Comparator<Persona> PER_COGNOME_E_NOME = Comparator
            .comparing((Persona p) -> p.cognome().toLowerCase(Locale.ITALIAN))
            .thenComparing(p -> p.nome().toLowerCase(Locale.ITALIAN))
            .thenComparing(p -> p.id().valore());

    private static final Comparator<Azienda> PER_RAGIONE_SOCIALE = Comparator
            .comparing((Azienda a) -> a.ragioneSociale().toLowerCase(Locale.ITALIAN))
            .thenComparing(a -> a.id().valore());

    private final PersoneRepository persone;
    private final AziendeRepository aziende;
    private final RapportiRepository rapporti;
    private final CatalogoCcnl catalogoCcnl;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final ApplicationEventPublisher eventi;

    AnagraficaService(PersoneRepository persone, AziendeRepository aziende, RapportiRepository rapporti,
                      CatalogoCcnl catalogoCcnl, CurrentWorkspaceProvider workspaceProvider,
                      ApplicationEventPublisher eventi) {
        this.persone = persone;
        this.aziende = aziende;
        this.rapporti = rapporti;
        this.catalogoCcnl = catalogoCcnl;
        this.workspaceProvider = workspaceProvider;
        this.eventi = eventi;
    }

    // ------------------------------------------------------------------------------------------ censimento

    @Override
    public Persona trovaOCreaPersona(DatiPersona dati) {
        var codiceFiscale = CodiceFiscalePersona.normalizza(dati.codiceFiscale());
        if (codiceFiscale == null) {
            throw new IllegalArgumentException("Codice fiscale della persona mancante");
        }
        if (codiceFiscale.length() > CodiceFiscalePersona.LUNGHEZZA) {
            throw new IllegalArgumentException("Codice fiscale della persona non valido: più di 16 caratteri");
        }
        if (!CodiceFiscalePersona.formalmenteValido(codiceFiscale)) {
            log.debug("Codice fiscale della persona formalmente non valido: accettato comunque");
        }
        var ws = workspace();
        var esistente = persone.trovaPerCodiceFiscale(ws, codiceFiscale);
        if (esistente.isPresent()) {
            var persona = esistente.get();
            if (persona.dataNascita() == null && dati.dataNascita() != null) {
                persone.aggiornaDataNascita(ws, persona.id(), dati.dataNascita());
                persona = new Persona(persona.id(), persona.codiceFiscale(), persona.nome(), persona.cognome(),
                        dati.dataNascita());
            }
            return persona;
        }
        var nome = Testi.pulisciETronca(NomiPropri.normalizza(dati.nome()), LUNGHEZZA_NOME);
        var cognome = Testi.pulisciETronca(NomiPropri.normalizza(dati.cognome()), LUNGHEZZA_NOME);
        if (nome == null || cognome == null) {
            throw new IllegalArgumentException("Nome e cognome della persona sono obbligatori");
        }
        var id = persone.inserisci(ws, codiceFiscale, nome, cognome, dati.dataNascita());
        log.info("Censita nuova persona (id {})", id.valore());
        return new Persona(id, codiceFiscale, nome, cognome, dati.dataNascita());
    }

    @Override
    public Azienda trovaOCreaAzienda(DatiAzienda dati) {
        var letta = ProfiloAzienda.daCedolino(dati);
        var ws = workspace();
        var candidate = aziende.candidate(ws, letta.identificativiFiscali(), letta.posizioneInps(),
                letta.ragioneSocialeNormalizzata());
        var abbinamento = AbbinamentoAzienda.trova(letta, candidate);
        if (abbinamento.isEmpty()) {
            var id = aziende.inserisci(ws, letta);
            log.info("Censita nuova azienda (id {})", id.valore());
            return letta.conId(id).comeAzienda();
        }
        var registrata = abbinamento.get().azienda();
        log.debug("Azienda {} riconosciuta per {}", registrata.id().valore(), abbinamento.get().criterio());
        return aggiorna(ws, registrata, letta);
    }

    @Override
    public Azienda aggiornaAziendaScelta(AziendaId id, DatiAzienda dati) {
        var letta = ProfiloAzienda.daCedolino(dati);
        var ws = workspace();
        var registrata = aziende.trova(ws, id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Azienda", id.valore()));
        log.debug("Azienda {} scelta dall'utente aggiornata", id.valore());
        return aggiorna(ws, registrata, letta);
    }

    /**
     * Aggiorna un'azienda registrata con i dati letti: attributi per recenza, identificativi mancanti completati se
     * non usati da altre aziende ({@link AggiornamentoAzienda}).
     */
    private Azienda aggiorna(WorkspaceId ws, ProfiloAzienda registrata, ProfiloAzienda letta) {
        var usatiDaAltre = aziende.identificativiFiscaliUsati(ws, letta.identificativiFiscali(), registrata.id());
        var aggiornata = AggiornamentoAzienda.unisci(registrata, letta, usatiDaAltre);
        if (!aggiornata.equals(registrata)) {
            aziende.aggiorna(ws, aggiornata);
        }
        return aggiornata.comeAzienda();
    }

    @Override
    public RapportoLavoro trovaOCreaRapporto(PersonaId personaId, AziendaId aziendaId, DatiRapporto dati) {
        var ws = workspace();
        if (persone.trova(ws, personaId).isEmpty()) {
            throw new RisorsaNonTrovataException("Persona", personaId.valore());
        }
        if (aziende.trova(ws, aziendaId).isEmpty()) {
            throw new RisorsaNonTrovataException("Azienda", aziendaId.valore());
        }
        var ccnl = ccnlValido(dati.ccnlCodiceRiconosciuto());
        var esistente = rapporti.trovaPerPersonaEAzienda(ws, personaId, aziendaId);
        if (esistente.isEmpty()) {
            var stato = AggiornamentoRapporto.unisci(StatoRapporto.vuoto(), dati, ccnl);
            var id = rapporti.inserisci(ws, personaId, aziendaId, stato);
            log.info("Creato rapporto di lavoro (id {}, persona {}, azienda {})", id.valore(), personaId.valore(),
                    aziendaId.valore());
            return rapportoEsistente(ws, id);
        }
        var registrato = esistente.get();
        var nuovoStato = AggiornamentoRapporto.unisci(registrato.stato(), dati, ccnl);
        if (!nuovoStato.equals(registrato.stato())) {
            rapporti.aggiornaStato(ws, registrato.rapporto().id(), nuovoStato);
            return rapportoEsistente(ws, registrato.rapporto().id());
        }
        return registrato.rapporto();
    }

    @Override
    public RapportoLavoro sovrascriviDatiRapporto(RapportoLavoroId rapportoId, DatiRapporto dati) {
        var ws = workspace();
        var registrato = rapporti.trova(ws, rapportoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Rapporto di lavoro", rapportoId.valore()));
        var nuovoStato = AggiornamentoRapporto.sovrascrivi(registrato.stato(), dati,
                ccnlValido(dati.ccnlCodiceRiconosciuto()));
        rapporti.aggiornaStato(ws, rapportoId, nuovoStato);
        return rapportoEsistente(ws, rapportoId);
    }

    @Override
    public void aggiornaEstremiCedolini(RapportoLavoroId rapportoId, YearMonth primoCedolino, YearMonth ultimoCedolino) {
        if (primoCedolino != null && ultimoCedolino != null && ultimoCedolino.isBefore(primoCedolino)) {
            throw new IllegalArgumentException("Estremi dei cedolini non validi: ultimo precedente al primo");
        }
        var ws = workspace();
        if (rapporti.trova(ws, rapportoId).isEmpty()) {
            throw new RisorsaNonTrovataException("Rapporto di lavoro", rapportoId.valore());
        }
        rapporti.aggiornaEstremiCedolini(ws, rapportoId, primoCedolino, ultimoCedolino);
    }

    @Override
    public void eliminaRapporto(RapportoLavoroId rapportoId) {
        var ws = workspace();
        var registrato = rapporti.trova(ws, rapportoId);
        if (registrato.isEmpty()) {
            log.debug("Eliminazione del rapporto {}: gia' assente", rapportoId.valore());
            return;
        }
        var rapporto = registrato.get().rapporto();
        eventi.publishEvent(new RapportoLavoroEliminato(rapportoId, rapporto.personaId(), rapporto.aziendaId()));
        rapporti.elimina(ws, rapportoId);
        log.info("Eliminato rapporto di lavoro (id {})", rapportoId.valore());
        if (!rapporti.esistonoPerPersona(ws, rapporto.personaId())) {
            persone.elimina(ws, rapporto.personaId());
            log.info("Eliminata persona rimasta senza rapporti (id {})", rapporto.personaId().valore());
        }
        if (!rapporti.esistonoPerAzienda(ws, rapporto.aziendaId())) {
            aziende.elimina(ws, rapporto.aziendaId());
            log.info("Eliminata azienda rimasta senza rapporti (id {})", rapporto.aziendaId().valore());
        }
    }

    // ------------------------------------------------------------------------------------------ consultazione

    @Override
    @Transactional(readOnly = true)
    public List<PersonaSintesi> elencoPersone() {
        var elenco = persone.elenco(workspace()).stream().sorted(PER_COGNOME_E_NOME).toList();
        var etichette = Etichette.persone(elenco);
        return elenco.stream()
                .map(p -> new PersonaSintesi(p.id(), p.nome(), p.cognome(), p.codiceFiscale(), p.dataNascita(),
                        etichette.get(p.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AziendaSelezionabile> elencoAziende() {
        var elenco = aziende.elenco(workspace()).stream().map(ProfiloAzienda::comeAzienda)
                .sorted(PER_RAGIONE_SOCIALE).toList();
        var etichette = Etichette.aziende(elenco);
        return elenco.stream().map(a -> new AziendaSelezionabile(a, etichette.get(a.id()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AziendaRiconosciuta> aziendaRiconosciuta(DatiAzienda dati) {
        if (RagioneSociale.normalizza(Testi.pulisciETronca(dati.ragioneSociale(), RagioneSociale.LUNGHEZZA)) == null) {
            return Optional.empty();
        }
        var letta = ProfiloAzienda.daCedolino(dati);
        var candidate = aziende.candidate(workspace(), letta.identificativiFiscali(), letta.posizioneInps(),
                letta.ragioneSocialeNormalizzata());
        return AbbinamentoAzienda.trova(letta, candidate)
                .map(esito -> new AziendaRiconosciuta(esito.azienda().id(), dato(letta, esito)));
    }

    /** Dato dei dati letti che determina l'abbinamento: la P.IVA se e' tra gli identificativi dell'azienda trovata. */
    private static AziendaRiconosciuta.Dato dato(ProfiloAzienda letta, AbbinamentoAzienda.Esito esito) {
        return switch (esito.criterio()) {
            case IDENTIFICATIVO_FISCALE -> letta.partitaIva() != null
                    && esito.azienda().identificativiFiscali().contains(letta.partitaIva())
                    ? AziendaRiconosciuta.Dato.PARTITA_IVA
                    : AziendaRiconosciuta.Dato.CODICE_FISCALE;
            case POSIZIONE_INPS -> AziendaRiconosciuta.Dato.POSIZIONE_INPS;
            case RAGIONE_SOCIALE -> AziendaRiconosciuta.Dato.RAGIONE_SOCIALE;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Persona> trovaPersona(PersonaId id) {
        return persone.trova(workspace(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Azienda> trovaAzienda(AziendaId id) {
        return aziende.trova(workspace(), id).map(ProfiloAzienda::comeAzienda);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RapportoLavoro> trovaRapporto(RapportoLavoroId id) {
        return rapporti.trova(workspace(), id).map(RapportiRepository.RapportoRegistrato::rapporto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RapportoSintesi> rapportiDiPersona(PersonaId personaId) {
        var ws = workspace();
        if (persone.trova(ws, personaId).isEmpty()) {
            throw new RisorsaNonTrovataException("Persona", personaId.valore());
        }
        var elenco = rapporti.perPersona(ws, personaId);
        var aziendePerId = aziendeDi(ws, elenco);
        var etichette = Etichette.aziende(aziendePerId.values());
        return PeriodiRapporti.ordina(elenco).stream()
                .map(r -> {
                    var rapporto = r.rapporto();
                    var azienda = aziendaDelRapporto(aziendePerId, rapporto);
                    return new RapportoSintesi(rapporto.id(), rapporto.personaId(), rapporto.aziendaId(),
                            azienda.ragioneSociale(), azienda.partitaIva(), etichette.get(azienda.id()), r.periodo(),
                            rapporto.ccnlCodice());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RapportoLavoroDettaglio> dettaglioRapporto(RapportoLavoroId id) {
        var ws = workspace();
        var registrato = rapporti.trova(ws, id);
        if (registrato.isEmpty()) {
            return Optional.empty();
        }
        var rapporto = registrato.get().rapporto();
        var persona = persone.trova(ws, rapporto.personaId())
                .orElseThrow(() -> new IllegalStateException("Persona del rapporto " + id.valore() + " non trovata"));
        var rapportiDellaPersona = rapporti.perPersona(ws, rapporto.personaId());
        var aziendePerId = aziendeDi(ws, rapportiDellaPersona);
        var azienda = aziendaDelRapporto(aziendePerId, rapporto);
        var periodo = PeriodiRapporti.calcola(rapportiDellaPersona).get(id);
        var etichetta = Etichette.aziende(aziendePerId.values()).get(azienda.id());
        return Optional.of(new RapportoLavoroDettaglio(rapporto, persona, azienda, etichetta, periodo));
    }

    // ------------------------------------------------------------------------------------------ gestione

    @Override
    public RapportoLavoro aggiornaNote(RapportoLavoroId rapportoId, String note) {
        var testo = note == null || note.isBlank() ? null : note.strip();
        if (testo != null && testo.length() > LUNGHEZZA_NOTE) {
            throw new IllegalArgumentException("Le note non possono superare " + LUNGHEZZA_NOTE + " caratteri");
        }
        var ws = workspace();
        var registrato = rapporti.trova(ws, rapportoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Rapporto di lavoro", rapportoId.valore()));
        if (Objects.equals(registrato.rapporto().note(), testo)) {
            return registrato.rapporto();
        }
        rapporti.aggiornaNote(ws, rapportoId, testo);
        return rapportoEsistente(ws, rapportoId);
    }

    @Override
    public RapportoLavoro impostaCcnl(RapportoLavoroId rapportoId, String ccnlCodice) {
        var ws = workspace();
        var registrato = rapporti.trova(ws, rapportoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Rapporto di lavoro", rapportoId.valore()));
        var ccnl = catalogoCcnl.trova(ccnlCodice)
                .orElseThrow(() -> new RisorsaNonTrovataException("CCNL", ccnlCodice));
        var precedente = registrato.rapporto().ccnlCodice();
        if (ccnl.codice().equals(precedente) && registrato.rapporto().ccnlSceltoDaUtente()) {
            return registrato.rapporto();
        }
        rapporti.aggiornaCcnlSceltoDaUtente(ws, rapportoId, ccnl.codice());
        if (!ccnl.codice().equals(precedente)) {
            eventi.publishEvent(new CcnlRapportoModificato(rapportoId, precedente, ccnl.codice()));
            log.info("CCNL del rapporto {} modificato dall'utente", rapportoId.valore());
        }
        return rapportoEsistente(ws, rapportoId);
    }

    // ------------------------------------------------------------------------------------------ supporto

    private WorkspaceId workspace() {
        return workspaceProvider.corrente();
    }

    private RapportoLavoro rapportoEsistente(WorkspaceId ws, RapportoLavoroId id) {
        return rapporti.trova(ws, id)
                .map(RapportiRepository.RapportoRegistrato::rapporto)
                .orElseThrow(() -> new IllegalStateException("Rapporto " + id.valore() + " non trovato"));
    }

    private Map<AziendaId, Azienda> aziendeDi(WorkspaceId ws, List<RapportoLavoro> elenco) {
        var ids = elenco.stream().map(RapportoLavoro::aziendaId).distinct().toList();
        var perId = new LinkedHashMap<AziendaId, Azienda>();
        aziende.trova(ws, ids).forEach(profilo -> perId.put(profilo.id(), profilo.comeAzienda()));
        return perId;
    }

    private static Azienda aziendaDelRapporto(Map<AziendaId, Azienda> aziendePerId, RapportoLavoro rapporto) {
        var azienda = aziendePerId.get(rapporto.aziendaId());
        if (azienda == null) {
            throw new IllegalStateException("Azienda del rapporto " + rapporto.id().valore() + " non trovata");
        }
        return azienda;
    }

    /** Codice CCNL riconosciuto solo se esiste in tabella (altrimenti il rapporto resta "da indicare"). */
    private String ccnlValido(String codice) {
        if (codice == null || codice.isBlank()) {
            return null;
        }
        var ccnl = catalogoCcnl.trova(codice).map(Ccnl::codice);
        if (ccnl.isEmpty()) {
            log.debug("CCNL riconosciuto non presente in tabella: ignorato");
        }
        return ccnl.orElse(null);
    }
}
