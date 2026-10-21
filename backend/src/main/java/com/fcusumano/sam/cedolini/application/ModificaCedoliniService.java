package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.CedolinoModificato;
import com.fcusumano.sam.cedolini.ModificaCedolini;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.cedolini.application.AcquisizioneCedolino.RegistrazioneModifica;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Acquisito;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.application.PreparazioneCedolinoManuale.Preparato;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.domain.DescrizioneCompetenza;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modifica a mano dei cedolini archiviati: i dati attuali dal JSON dell'estrazione ({@link DatiCedolinoArchiviato}),
 * la stessa preparazione dell'inserimento a mano ({@link PreparazioneCedolinoManuale}) e la sostituzione del cedolino
 * in una transazione serializzata ({@link AcquisizioneCedolino#modificaManuale}); la verifica la esegue e la annulla.
 * Il cedolino modificato e' registrato con il modello {@link ModelliEstrazione#MODELLO_MODIFICA_MANUALE}. Nei log solo
 * id.
 */
@Service
class ModificaCedoliniService implements ModificaCedolini {

    private static final Logger log = LoggerFactory.getLogger(ModificaCedoliniService.class);

    static final String AVVISO_ALTRO_RAPPORTO = "Il cedolino passerà a un altro rapporto di lavoro: persona o azienda "
            + "sono diverse da quelle attuali";
    static final String AVVISO_RAPPORTO_ELIMINATO = "Il rapporto di lavoro attuale resterà senza cedolini e verrà "
            + "eliminato";
    static final String DATI_NON_LEGGIBILI = "I dati archiviati di questo cedolino non sono più leggibili: "
            + "eliminalo e caricalo di nuovo";

    private final CedoliniRepository cedolini;
    private final ConsultazioneAnagrafica consultazione;
    private final AcquisizioneCedolino acquisizione;
    private final SbloccoPdfArchiviati sbloccoArchiviati;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final PreparazioneCedolinoManuale preparazione;
    private final InterpreteRispostaEstrazione interprete;

    ModificaCedoliniService(CedoliniRepository cedolini, ConsultazioneAnagrafica consultazione,
                            AcquisizioneCedolino acquisizione, SbloccoPdfArchiviati sbloccoArchiviati,
                            CurrentWorkspaceProvider workspaceProvider, Clock clock) {
        this.cedolini = cedolini;
        this.consultazione = consultazione;
        this.acquisizione = acquisizione;
        this.sbloccoArchiviati = sbloccoArchiviati;
        this.workspaceProvider = workspaceProvider;
        this.preparazione = new PreparazioneCedolinoManuale(consultazione, clock);
        this.interprete = InterpreteRispostaEstrazione.standard(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public CedolinoManuale dati(CedolinoId id) {
        var ws = workspaceProvider.corrente();
        var cedolino = cedolini.trova(ws, id).orElseThrow(() -> new RisorsaNonTrovataException("Cedolino", id.valore()));
        var documento = cedolini.documento(ws, id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Cedolino", id.valore()));
        var rapporto = consultazione.trovaRapporto(cedolino.rapportoId())
                .orElseThrow(() -> new IllegalStateException("Rapporto del cedolino " + id.valore() + " non trovato"));
        try {
            return DatiCedolinoArchiviato.leggi(documento.jsonEstrazione(), documento.modello(), rapporto, interprete);
        } catch (EstrazioneCedolinoException e) {
            log.warn("Dati archiviati del cedolino {} non interpretabili ({})", id.valore(), e.motivo());
            throw new OperazioneNonConsentitaException(DATI_NON_LEGGIBILI);
        }
    }

    @Override
    public VerificaCedolinoManuale verifica(CedolinoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = preparazione.prepara(dati, ModelliEstrazione.MODELLO_MODIFICA_MANUALE);
        var precedente = cedolini.trova(ws, id).map(CedoliniRepository.CedolinoRegistrato::competenza).orElse(null);
        var modifica = modifica(ws, id, preparato, true);
        var registrazione = modifica.registrazione();
        var avvisi = new ArrayList<>(preparato.risultato().avvisi());
        avvisi.addAll(avvisiDiContesto(modifica, precedente));
        var rapporto = registrazione.rapporto();
        var rapportoEsistente = modifica.nuovoRapporto() || rapporto == null ? null : rapporto.id();
        return switch (registrazione.esito()) {
            case Acquisito a -> new VerificaCedolinoManuale(Esito.ACQUISIBILE, null, registrazione.competenza(),
                    modifica.nuovaPersona(), modifica.nuovaAzienda(), modifica.nuovoRapporto(), rapportoEsistente,
                    null, avvisi);
            case Scartato s -> new VerificaCedolinoManuale(
                    s.stato() == StatoCaricamento.DUPLICATO ? Esito.DUPLICATO : Esito.AGGIUNTIVO_NON_GESTITO,
                    s.messaggio(), null, modifica.nuovaPersona(), modifica.nuovaAzienda(), modifica.nuovoRapporto(),
                    rapportoEsistente, s.esistente(), avvisi);
        };
    }

    @Override
    public CedolinoModificato modifica(CedolinoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = preparazione.prepara(dati, ModelliEstrazione.MODELLO_MODIFICA_MANUALE);
        var modifica = modifica(ws, id, preparato, false);
        var registrazione = modifica.registrazione();
        return switch (registrazione.esito()) {
            case Acquisito a -> {
                // il PDF archiviato puo' essere ancora protetto o da verificare: si prova a sbloccarlo
                sbloccoArchiviati.avvia(ws);
                yield new CedolinoModificato(a.cedolinoId(), registrazione.rapporto().id(), registrazione.competenza(),
                        modifica.rapportoEliminato());
            }
            case Scartato s -> {
                // la transazione e' gia' annullata: il cedolino resta com'era
                log.info("Modifica a mano del cedolino {} scartata: {}", id.valore(), s.stato());
                throw new OperazioneNonConsentitaException(s.messaggio());
            }
        };
    }

    private RegistrazioneModifica modifica(WorkspaceId ws, CedolinoId id, Preparato preparato, boolean prova) {
        return PreparazioneCedolinoManuale.conErroriSuiCampi("Modifica a mano del cedolino " + id.valore(),
                () -> acquisizione.modificaManuale(ws, id, preparato.risultato(), prova, preparato.scelte()));
    }

    /**
     * Avvisi della prova sul contesto, non salvati: periodo o rapporto diversi da quelli attuali, rapporto attuale
     * eliminato, persona, azienda o rapporto che verrebbero creati.
     */
    private static List<String> avvisiDiContesto(RegistrazioneModifica modifica, Competenza precedente) {
        var avvisi = new ArrayList<String>();
        var competenza = modifica.registrazione().competenza();
        if (precedente != null && competenza != null && !precedente.equals(competenza)) {
            avvisi.add("Il cedolino passerà da " + DescrizioneCompetenza.di(precedente) + " a "
                    + DescrizioneCompetenza.di(competenza));
        }
        if (modifica.nuovaPersona()) {
            avvisi.add(CaricamentiInErroreService.AVVISO_NUOVA_PERSONA);
        }
        if (modifica.nuovaAzienda()) {
            avvisi.add(CaricamentiInErroreService.AVVISO_NUOVA_AZIENDA);
        }
        if (modifica.nuovoRapporto() && !modifica.nuovaPersona() && !modifica.nuovaAzienda()) {
            avvisi.add(CaricamentiInErroreService.AVVISO_NUOVO_RAPPORTO);
        }
        if (modifica.registrazione().esito() instanceof Acquisito) {
            if (modifica.altroRapporto()) {
                avvisi.add(AVVISO_ALTRO_RAPPORTO);
            }
            if (modifica.rapportoEliminato()) {
                avvisi.add(AVVISO_RAPPORTO_ELIMINATO);
            }
        }
        return avvisi;
    }
}
