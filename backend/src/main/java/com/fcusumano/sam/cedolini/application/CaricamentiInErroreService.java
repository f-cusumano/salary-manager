package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.cedolini.application.PreparazioneCedolinoManuale.Preparato;
import com.fcusumano.sam.cedolini.application.AcquisizioneCedolino.RegistrazioneManuale;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileDelCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Acquisito;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.domain.DescrizioneCompetenza;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Azioni sui file caricati con errore. Ogni azione che modifica un file ricontrolla, nella transazione serializzata
 * per workspace ({@link ScrittureSerializzate}), che il file sia ancora in errore e non in elaborazione
 * ({@link ControlliFileInErrore}).
 * <p>
 * L'inserimento a mano e' un lettore in piu': il cedolino inserito, validato ({@link ValidazioneCedolinoManuale}),
 * diventa il JSON dello schema di lettura ({@link JsonCedolinoManuale}), passa dall'interprete comune a tutti i lettori
 * e viene archiviato da {@link AcquisizioneCedolino} con il PDF originale conservato del file; la verifica esegue la
 * stessa acquisizione e la annulla. Persona e azienda scelte dall'elenco portano i loro dati in archivio al posto di
 * quelli inseriti; quelle nuove non devono essere gia' in archivio ({@link AnagraficheScelte}). Nei log solo id,
 * motivi e tipi di eccezione.
 */
@Service
class CaricamentiInErroreService implements CaricamentiInErrore {

    private static final Logger log = LoggerFactory.getLogger(CaricamentiInErroreService.class);

    static final String AVVISO_NUOVA_PERSONA = "Verrà creata una nuova persona: controlla il codice fiscale";
    static final String AVVISO_NUOVA_AZIENDA =
            "Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale";
    static final String AVVISO_NUOVO_RAPPORTO = "Verrà creato un nuovo rapporto di lavoro: la persona scelta non ha "
            + "ancora cedolini con l'azienda scelta";

    private final CaricamentiRepository caricamenti;
    private final CedoliniRepository cedolini;
    private final ScrittureSerializzate scritture;
    private final AcquisizioneCedolino acquisizione;
    private final ElaborazioneCaricamenti elaborazione;
    private final CaricamentoCedolini caricamentoCedolini;
    private final ConsultazioneAnagrafica consultazione;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final ApplicationEventPublisher eventi;
    private final Clock clock;
    private final PreparazioneCedolinoManuale preparazione;
    private final SbloccoPdfArchiviati sbloccoArchiviati;

    CaricamentiInErroreService(CaricamentiRepository caricamenti, CedoliniRepository cedolini,
                               ScrittureSerializzate scritture, AcquisizioneCedolino acquisizione,
                               ElaborazioneCaricamenti elaborazione, CaricamentoCedolini caricamentoCedolini,
                               ConsultazioneAnagrafica consultazione, CurrentWorkspaceProvider workspaceProvider,
                               ApplicationEventPublisher eventi, Clock clock,
                               SbloccoPdfArchiviati sbloccoArchiviati) {
        this.caricamenti = caricamenti;
        this.cedolini = cedolini;
        this.scritture = scritture;
        this.acquisizione = acquisizione;
        this.elaborazione = elaborazione;
        this.caricamentoCedolini = caricamentoCedolini;
        this.consultazione = consultazione;
        this.workspaceProvider = workspaceProvider;
        this.eventi = eventi;
        this.clock = clock;
        this.preparazione = new PreparazioneCedolinoManuale(consultazione, clock);
        this.sbloccoArchiviati = sbloccoArchiviati;
    }

    // ------------------------------------------------------------------------------------------ letture

    @Override
    @Transactional(readOnly = true)
    public List<CaricamentoInErrore> elenco() {
        return caricamenti.inErrore(workspaceProvider.corrente());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaricamentoInErrore> associatiARapporto(RapportoLavoroId rapportoId) {
        return caricamenti.inErroreDiRapporto(workspaceProvider.corrente(), rapportoId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PdfCedolino> pdf(CaricamentoId id) {
        var ws = workspaceProvider.corrente();
        return caricamenti.fileDelCaricamento(ws, id)
                .filter(file -> file.stato() == StatoCaricamento.ERRORE && file.inErrore() && file.pdfPresente())
                .flatMap(file -> caricamenti.pdf(ws, file.hash())
                        .map(contenuto -> new PdfCedolino(file.nomeFileOriginale(), contenuto)));
    }

    // ------------------------------------------------------------------------------------------ associazione

    @Override
    public CaricamentoInErrore associa(CaricamentoId id, RapportoLavoroId rapporto, Integer anno, Integer mese) {
        var competenza = competenzaDaAssociare(rapporto, anno, mese);
        var ws = workspaceProvider.corrente();
        return scritture.inTransazione(ws, stato -> {
            var file = ControlliFileInErrore.daRisolvere(caricamenti, ws, id);
            if (consultazione.trovaRapporto(rapporto).isEmpty()) {
                throw new RisorsaNonTrovataException("Rapporto di lavoro", rapporto.valore());
            }
            if (cedolini.perChiave(ws, rapporto, competenza).isPresent()) {
                throw new OperazioneNonConsentitaException(MessaggiCaricamento.competenzaGiaPresente(competenza));
            }
            if (caricamenti.hashAssociatoA(ws, rapporto, competenza).filter(h -> !h.equals(file.hash())).isPresent()) {
                throw new OperazioneNonConsentitaException(MessaggiCaricamento.competenzaGiaAssociata(competenza));
            }
            var modificati = new LinkedHashSet<RapportoLavoroId>();
            caricamenti.eliminaAttribuzione(ws, file.hash()).ifPresent(modificati::add);
            caricamenti.attribuisci(ws, file.hash(), rapporto, competenza);
            modificati.add(rapporto);
            eventi.publishEvent(new CaricamentiInErroreModificati(modificati));
            log.info("File in errore del caricamento {} associato al rapporto {} (competenza {})", id.valore(),
                    rapporto.valore(), competenza.codice());
            return riga(ws, file);
        });
    }

    @Override
    public CaricamentoInErrore rimuoviAssociazione(CaricamentoId id) {
        var ws = workspaceProvider.corrente();
        return scritture.inTransazione(ws, stato -> {
            var file = ControlliFileInErrore.daRisolvere(caricamenti, ws, id);
            caricamenti.eliminaAttribuzione(ws, file.hash()).ifPresent(rapporto -> {
                eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(rapporto)));
                log.info("Rimossa l'associazione del file in errore del caricamento {}", id.valore());
            });
            return riga(ws, file);
        });
    }

    /** Mensilita' da associare: rapporto, anno (dal 1900 all'anno prossimo) e mese 1..14 obbligatori. */
    private Competenza.Mensilita competenzaDaAssociare(RapportoLavoroId rapporto, Integer anno, Integer mese) {
        int annoMassimo = Year.now(clock).getValue() + 1;
        var errori = new ArrayList<ErroreCampo>();
        if (rapporto == null) {
            errori.add(new ErroreCampo("rapportoId", "Indica il rapporto di lavoro"));
        }
        if (anno == null || anno < ValidazioneCedolinoManuale.PRIMO_ANNO || anno > annoMassimo) {
            errori.add(new ErroreCampo("anno",
                    "Indica l'anno (dal " + ValidazioneCedolinoManuale.PRIMO_ANNO + " al " + annoMassimo + ")"));
        }
        if (mese == null || mese < 1 || mese > 14) {
            errori.add(new ErroreCampo("mese",
                    "Indica il mese (da 1 a 12, 13 per la tredicesima, 14 per la quattordicesima)"));
        }
        if (!errori.isEmpty()) {
            throw new DatiNonValidiException(errori);
        }
        return new Competenza.Mensilita(anno, mese);
    }

    // ------------------------------------------------------------------------------------------ rimozione, rilettura

    @Override
    public void rimuovi(CaricamentoId id) {
        var ws = workspaceProvider.corrente();
        scritture.inTransazione(ws, stato -> {
            var file = ControlliFileInErrore.daRisolvere(caricamenti, ws, id);
            var associato = caricamenti.eliminaAttribuzione(ws, file.hash());
            caricamenti.eliminaPdf(ws, file.hash());
            int eliminati = caricamenti.eliminaErroriDelFile(ws, file.hash());
            associato.ifPresent(rapporto -> eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(rapporto))));
            log.info("Rimosso il file in errore del caricamento {} ({} tentativi in errore)", id.valore(), eliminati);
            return null;
        });
    }

    @Override
    public List<Caricamento> rileggi(CaricamentoId id, String password) {
        var ws = workspaceProvider.corrente();
        var passwordLettura = password == null || password.isEmpty() ? null : password;
        var lettura = scritture.inTransazione(ws, stato -> {
            var file = ControlliFileInErrore.daRisolvere(caricamenti, ws, id);
            var pdf = caricamenti.pdf(ws, file.hash())
                    .orElseThrow(() -> new OperazioneNonConsentitaException(MessaggiCaricamento.PDF_NON_CONSERVATO));
            var registrato = caricamenti.registra(ws, UUID.randomUUID(),
                    List.of(new FileInCoda(file.nomeFileOriginale(), file.dimensione(), file.hash()))).getFirst();
            return new NuovaLettura(registrato,
                    new Lavoro(ws, registrato.id(), file.nomeFileOriginale(), pdf, file.hash(), passwordLettura));
        });
        // dopo il commit: il nuovo tentativo e' visibile (file in elaborazione) prima che la lettura cominci
        elaborazione.accoda(List.of(lettura.lavoro()));
        log.info("Nuova lettura del file in errore del caricamento {}: caricamento {}", id.valore(),
                lettura.registrato().id().valore());
        return List.of(lettura.registrato());
    }

    @Override
    public List<Caricamento> rileggiProtetti() {
        var ws = workspaceProvider.corrente();
        var letture = scritture.inTransazione(ws, stato -> {
            var file = caricamenti.inErrore(ws).stream()
                    .filter(f -> f.motivo() == MotivoErrore.PDF_PROTETTO && f.pdfDisponibile() && !f.inElaborazione())
                    .toList();
            if (file.isEmpty()) {
                return List.<NuovaLettura>of();
            }
            var pdf = new ArrayList<byte[]>(file.size());
            var inCoda = new ArrayList<FileInCoda>(file.size());
            for (var f : file) {
                var dettaglio = ControlliFileInErrore.daRisolvere(caricamenti, ws, f.id());
                pdf.add(caricamenti.pdf(ws, dettaglio.hash()).orElseThrow(
                        () -> new OperazioneNonConsentitaException(MessaggiCaricamento.PDF_NON_CONSERVATO)));
                inCoda.add(new FileInCoda(dettaglio.nomeFileOriginale(), dettaglio.dimensione(), dettaglio.hash()));
            }
            var registrati = caricamenti.registra(ws, UUID.randomUUID(), inCoda);
            var nuove = new ArrayList<NuovaLettura>(registrati.size());
            for (int i = 0; i < registrati.size(); i++) {
                nuove.add(new NuovaLettura(registrati.get(i), new Lavoro(ws, registrati.get(i).id(),
                        inCoda.get(i).nomeFileOriginale(), pdf.get(i), inCoda.get(i).hash(), null)));
            }
            return nuove;
        });
        if (letture.isEmpty()) {
            return List.of();
        }
        elaborazione.accoda(letture.stream().map(NuovaLettura::lavoro).toList());
        log.info("Nuova lettura di {} file in errore protetti da password", letture.size());
        return letture.stream().map(NuovaLettura::registrato).toList();
    }

    /** Tentativo registrato e lavoro da accodare (la password resta solo in memoria). */
    private record NuovaLettura(Caricamento registrato, Lavoro lavoro) {
    }

    // ------------------------------------------------------------------------------------------ inserimento a mano

    @Override
    public VerificaCedolinoManuale verifica(CaricamentoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = preparazione.prepara(dati, ModelliEstrazione.MODELLO_INSERIMENTO_MANUALE);
        var manuale = acquisisci(ws, id, preparato, true);
        var registrazione = manuale.registrazione();
        var avvisi = new ArrayList<>(preparato.risultato().avvisi());
        avvisi.addAll(avvisiDiContesto(manuale));
        var rapporto = registrazione.rapporto();
        var rapportoEsistente = manuale.nuovoRapporto() || rapporto == null ? null : rapporto.id();
        return switch (registrazione.esito()) {
            case Acquisito a -> new VerificaCedolinoManuale(Esito.ACQUISIBILE, null, registrazione.competenza(),
                    manuale.nuovaPersona(), manuale.nuovaAzienda(), manuale.nuovoRapporto(), rapportoEsistente, null,
                    avvisi);
            case Scartato s -> new VerificaCedolinoManuale(
                    s.stato() == StatoCaricamento.DUPLICATO ? Esito.DUPLICATO : Esito.AGGIUNTIVO_NON_GESTITO,
                    s.messaggio(), null, manuale.nuovaPersona(), manuale.nuovaAzienda(), manuale.nuovoRapporto(),
                    rapportoEsistente, s.esistente(), avvisi);
        };
    }

    @Override
    public Caricamento inserisci(CaricamentoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = preparazione.prepara(dati, ModelliEstrazione.MODELLO_INSERIMENTO_MANUALE);
        var manuale = acquisisci(ws, id, preparato, false);
        return switch (manuale.registrazione().esito()) {
            case Acquisito a -> {
                log.info("Cedolino {} inserito a mano (caricamento {})", a.cedolinoId().valore(), id.valore());
                // il PDF conservato puo' essere ancora protetto: si prova a sbloccarlo con le password note
                sbloccoArchiviati.avvia(ws);
                yield caricamentoCedolini.stato(List.of(id)).getFirst();
            }
            case Scartato s -> {
                // la transazione e' gia' annullata: il file resta in errore con il suo PDF
                log.info("Inserimento a mano del caricamento {} scartato: {}", id.valore(), s.stato());
                throw new OperazioneNonConsentitaException(s.messaggio());
            }
        };
    }

    /** Acquisizione (o prova) del cedolino inserito, con gli errori sui campi. */
    private RegistrazioneManuale acquisisci(WorkspaceId ws, CaricamentoId id, Preparato preparato, boolean prova) {
        return PreparazioneCedolinoManuale.conErroriSuiCampi("Inserimento a mano del caricamento " + id.valore(),
                () -> acquisizione.acquisisciManuale(ws, id, preparato.risultato(), prova, preparato.scelte()));
    }

    /**
     * Avvisi della prova sul contesto, non salvati: mese o rapporto diversi da quelli associati al file, persona,
     * azienda o rapporto che verrebbero creati (il rapporto nuovo solo tra anagrafiche gia' in archivio: con una
     * persona o un'azienda nuova e' ovvio).
     */
    private static List<String> avvisiDiContesto(RegistrazioneManuale manuale) {
        var avvisi = new ArrayList<String>();
        var attribuzione = manuale.attribuzione();
        var competenza = manuale.registrazione().competenza();
        var rapporto = manuale.registrazione().rapporto();
        if (attribuzione != null && competenza != null && !attribuzione.competenza().equals(competenza)) {
            avvisi.add("Il periodo indicato è diverso dal mese associato al file ("
                    + DescrizioneCompetenza.di(attribuzione.competenza()) + ")");
        }
        if (attribuzione != null && rapporto != null && !attribuzione.rapportoId().equals(rapporto.id())) {
            avvisi.add(avvisoRapportoDiverso(attribuzione.competenza()));
        }
        if (manuale.nuovaPersona()) {
            avvisi.add(AVVISO_NUOVA_PERSONA);
        }
        if (manuale.nuovaAzienda()) {
            avvisi.add(AVVISO_NUOVA_AZIENDA);
        }
        if (manuale.nuovoRapporto() && !manuale.nuovaPersona() && !manuale.nuovaAzienda()) {
            avvisi.add(AVVISO_NUOVO_RAPPORTO);
        }
        return avvisi;
    }

    /** Il cedolino andrebbe a un rapporto diverso da quello del mese a cui l'utente ha associato il file. */
    static String avvisoRapportoDiverso(Competenza associata) {
        return "Persona o azienda diverse da quelle del mese associato al file (" + DescrizioneCompetenza.di(associata)
                + ")";
    }

    /** Il file come elencato, letto nella transazione dell'azione. */
    private CaricamentoInErrore riga(WorkspaceId ws, FileDelCaricamento file) {
        return caricamenti.inErrore(ws, file.hash())
                .orElseThrow(() -> new IllegalStateException("File in errore non piu' elencato dopo l'azione"));
    }
}
