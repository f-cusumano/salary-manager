package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.cedolini.application.AcquisizioneCedolino.RegistrazioneManuale;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileDelCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Acquisito;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * stessa acquisizione e la annulla. Nei log solo id, motivi e tipi di eccezione.
 */
@Service
class CaricamentiInErroreService implements CaricamentiInErrore {

    private static final Logger log = LoggerFactory.getLogger(CaricamentiInErroreService.class);

    static final String AVVISO_NOME_DIVERSO =
            "Nome e cognome in archivio per questo codice fiscale sono diversi: restano quelli in archivio";
    static final String AVVISO_NUOVA_PERSONA = "Verrà creata una nuova persona: controlla il codice fiscale";
    static final String AVVISO_NUOVO_RAPPORTO = "Verrà creato un nuovo rapporto di lavoro: se l'azienda esiste già "
            + "controlla ragione sociale, partita IVA e codice fiscale";

    private static final Pattern SPAZI = Pattern.compile("\\s+");

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
    private final ValidazioneCedolinoManuale validazione;
    private final InterpreteRispostaEstrazione interprete;

    CaricamentiInErroreService(CaricamentiRepository caricamenti, CedoliniRepository cedolini,
                               ScrittureSerializzate scritture, AcquisizioneCedolino acquisizione,
                               ElaborazioneCaricamenti elaborazione, CaricamentoCedolini caricamentoCedolini,
                               ConsultazioneAnagrafica consultazione, CurrentWorkspaceProvider workspaceProvider,
                               ApplicationEventPublisher eventi, Clock clock) {
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
        this.validazione = new ValidazioneCedolinoManuale(clock);
        this.interprete = InterpreteRispostaEstrazione.standard(clock);
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
                .filter(file -> file.stato() == StatoCaricamento.ERRORE && file.pdfPresente())
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

    /** Tentativo registrato e lavoro da accodare (la password resta solo in memoria). */
    private record NuovaLettura(Caricamento registrato, Lavoro lavoro) {
    }

    // ------------------------------------------------------------------------------------------ inserimento a mano

    @Override
    public VerificaCedolinoManuale verifica(CaricamentoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = prepara(dati);
        var manuale = acquisisci(ws, id, preparato.risultato(), true);
        var registrazione = manuale.registrazione();
        var avvisi = new ArrayList<>(preparato.risultato().avvisi());
        avvisi.addAll(avvisiDiContesto(preparato.normalizzato(), manuale));
        var rapporto = registrazione.rapporto();
        var rapportoEsistente = manuale.nuovoRapporto() || rapporto == null ? null : rapporto.id();
        return switch (registrazione.esito()) {
            case Acquisito a -> new VerificaCedolinoManuale(Esito.ACQUISIBILE, null, registrazione.competenza(),
                    manuale.nuovaPersona(), manuale.nuovoRapporto(), rapportoEsistente, null, avvisi);
            case Scartato s -> new VerificaCedolinoManuale(
                    s.stato() == StatoCaricamento.DUPLICATO ? Esito.DUPLICATO : Esito.AGGIUNTIVO_NON_GESTITO,
                    s.messaggio(), null, manuale.nuovaPersona(), manuale.nuovoRapporto(), rapportoEsistente,
                    s.esistente(), avvisi);
        };
    }

    @Override
    public Caricamento inserisci(CaricamentoId id, CedolinoManuale dati) {
        var ws = workspaceProvider.corrente();
        var preparato = prepara(dati);
        var manuale = acquisisci(ws, id, preparato.risultato(), false);
        return switch (manuale.registrazione().esito()) {
            case Acquisito a -> {
                log.info("Cedolino {} inserito a mano (caricamento {})", a.cedolinoId().valore(), id.valore());
                yield caricamentoCedolini.stato(List.of(id)).getFirst();
            }
            case Scartato s -> {
                // la transazione e' gia' annullata: il file resta in errore con il suo PDF
                log.info("Inserimento a mano del caricamento {} scartato: {}", id.valore(), s.stato());
                throw new OperazioneNonConsentitaException(s.messaggio());
            }
        };
    }

    /** Cedolino inserito normalizzato e risultato equivalente a una lettura. */
    private record Preparato(CedolinoManuale normalizzato, RisultatoEstrazione risultato) {
    }

    /**
     * Validazione bloccante, normalizzazione, JSON dello schema di lettura e interpretazione (come per ogni lettore);
     * confidenza azzerata (nessuna lettura) e avvisi propri dell'inserimento a mano aggiunti a quelli dei controlli.
     *
     * @throws DatiNonValidiException se ci sono errori bloccanti
     */
    private Preparato prepara(CedolinoManuale dati) {
        var codiciFiscali = consultazione.elencoPersone().stream().map(PersonaSintesi::codiceFiscale)
                .collect(Collectors.toSet());
        var errori = validazione.verifica(dati, codiciFiscali);
        if (!errori.isEmpty()) {
            throw new DatiNonValidiException(errori);
        }
        var normalizzato = ValidazioneCedolinoManuale.normalizza(dati);
        RisultatoEstrazione letto;
        try {
            letto = interprete.interpreta(JsonCedolinoManuale.scrivi(normalizzato),
                    ModelliEstrazione.MODELLO_INSERIMENTO_MANUALE);
        } catch (EstrazioneCedolinoException e) {
            // la validazione rende il JSON sempre conforme: e' un difetto, non un errore dell'utente
            throw new IllegalStateException("Cedolino inserito a mano non interpretabile (" + e.motivo() + ")", e);
        }
        var avvisi = new ArrayList<>(letto.avvisi());
        avvisi.addAll(ValidazioneCedolinoManuale.avvisi(normalizzato));
        return new Preparato(normalizzato, new RisultatoEstrazione(letto.cedolino().conConfidenza(null),
                letto.jsonOriginale(), letto.modello(), avvisi));
    }

    /**
     * Acquisizione (o prova) del cedolino inserito. I dati indispensabili mancanti e i valori rifiutati da anagrafica o
     * competenza, che la validazione dovrebbe gia' escludere, diventano errori sul campo corrispondente.
     */
    private RegistrazioneManuale acquisisci(WorkspaceId ws, CaricamentoId id, RisultatoEstrazione risultato,
                                            boolean prova) {
        try {
            return acquisizione.acquisisciManuale(ws, id, risultato, prova);
        } catch (CedolinoNonAcquisibileException e) {
            log.info("Inserimento a mano del caricamento {} non acquisibile: dato {}", id.valore(), e.dato());
            throw new DatiNonValidiException(campo(e.dato()), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.info("Inserimento a mano del caricamento {} rifiutato: {}", id.valore(), e.getClass().getSimpleName());
            throw new DatiNonValidiException(null, e.getMessage());
        }
    }

    private static String campo(CedolinoNonAcquisibileException.Dato dato) {
        return switch (dato) {
            case PERIODO -> "periodo.mese";
            case CODICE_FISCALE -> "dipendente.codiceFiscale";
            case DATORE -> "datoreLavoro.ragioneSociale";
        };
    }

    /**
     * Avvisi della prova sul contesto, non salvati: nome diverso da quello in archivio, mese diverso da quello
     * associato al file, persona o rapporto che verrebbero creati.
     */
    private static List<String> avvisiDiContesto(CedolinoManuale normalizzato, RegistrazioneManuale manuale) {
        var avvisi = new ArrayList<String>();
        var persona = manuale.personaPrima();
        var dipendente = normalizzato.dipendente();
        if (persona != null && !(stessoNome(persona.nome(), dipendente.nome())
                && stessoNome(persona.cognome(), dipendente.cognome()))) {
            avvisi.add(AVVISO_NOME_DIVERSO);
        }
        var attribuzione = manuale.attribuzione();
        var competenza = manuale.registrazione().competenza();
        if (attribuzione != null && competenza != null && !attribuzione.competenza().equals(competenza)) {
            avvisi.add("Il periodo indicato è diverso dal mese associato al file ("
                    + DescrizioneCompetenza.di(attribuzione.competenza()) + ")");
        }
        if (manuale.nuovaPersona()) {
            avvisi.add(AVVISO_NUOVA_PERSONA);
        }
        if (manuale.nuovoRapporto()) {
            avvisi.add(AVVISO_NUOVO_RAPPORTO);
        }
        return avvisi;
    }

    /** Stesso nome a meno di maiuscole e spazi (in archivio i nomi propri sono normalizzati). */
    private static boolean stessoNome(String archivio, String inserito) {
        return confrontabile(archivio).equals(confrontabile(inserito));
    }

    private static String confrontabile(String nome) {
        return nome == null ? "" : SPAZI.matcher(nome.strip()).replaceAll(" ").toLowerCase(Locale.ITALIAN);
    }

    /** Il file come elencato, letto nella transazione dell'azione. */
    private CaricamentoInErrore riga(WorkspaceId ws, FileDelCaricamento file) {
        return caricamenti.inErrore(ws, file.hash())
                .orElseThrow(() -> new IllegalStateException("File in errore non piu' elencato dopo l'azione"));
    }
}
