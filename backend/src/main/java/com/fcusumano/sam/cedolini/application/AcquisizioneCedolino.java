package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.DatiPersona;
import com.fcusumano.sam.anagrafica.DatiRapporto;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.AnagraficheScelte.Scelte;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.AttribuzioneSalvata;
import com.fcusumano.sam.cedolini.application.CedoliniRepository.CedolinoConImporti;
import com.fcusumano.sam.cedolini.application.CedoliniRepository.NuovoCedolino;
import com.fcusumano.sam.cedolini.application.CedolinoNonAcquisibileException.Dato;
import com.fcusumano.sam.cedolini.application.ClassificazioneCedolini.CedolinoInArrivo;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Acquisito;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.cedolini.domain.ConfrontoCedolini;
import com.fcusumano.sam.cedolini.domain.ConfrontoCedolini.ImportiChiave;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.DiFineRapporto;
import com.fcusumano.sam.cedolini.domain.DeterminazioneCompetenza.Mensile;
import com.fcusumano.sam.cedolini.domain.EstremiCedolini;
import com.fcusumano.sam.cedolini.domain.NomeFileCedolinoStrategy;
import com.fcusumano.sam.cedolini.domain.TipoDichiarato;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Fase di persistenza di un caricamento, in una transazione serializzata per workspace: censimento di persona,
 * azienda e rapporto, determinazione della competenza, controllo di unicita' (duplicato / aggiuntivo), salvataggio
 * di cedolino e PDF (senza protezione se era cifrato), riclassificazione dei cedolini del rapporto e rinumerazione dei progressivi TFR
 * ({@link ClassificazioneCedolini}), estremi del rapporto, caricamento ACQUISITO (gli eventuali tentativi in errore
 * successivi dello stesso file diventano DUPLICATO), eliminazione di PDF conservato e associazione del file (non piu'
 * in errore) e infine pubblicazione di {@link CedolinoAcquisito} (i ricalcoli dei
 * moduli calcoli e segnalazioni avvengono nella stessa transazione e vedono uno stato gia' coerente).
 * Un cedolino scartato annulla la transazione: non resta alcuna anagrafica creata per lui.
 * <p>
 * L'inserimento a mano di un file in errore ({@link #acquisisciManuale}) passa dalla stessa registrazione con il PDF
 * originale conservato; la sua prova esegue l'acquisizione per intero e annulla la transazione (i listener degli
 * eventi sono sincroni: anche i loro ricalcoli vengono annullati). Persona e azienda scelte dall'elenco sono usate
 * esattamente ({@link AnagraficheScelte}): la persona e' ritrovata per il codice fiscale in archivio, l'azienda e'
 * collegata per id senza abbinamento, e il rapporto risultante deve essere il loro.
 * Nei log compaiono solo id.
 */
@Component
class AcquisizioneCedolino {

    private static final Logger log = LoggerFactory.getLogger(AcquisizioneCedolino.class);

    private final ScrittureSerializzate scritture;
    private final CedoliniRepository cedolini;
    private final CaricamentiRepository caricamenti;
    private final CensimentoAnagrafica censimento;
    private final ConsultazioneAnagrafica consultazione;
    private final CatalogoCcnl catalogoCcnl;
    private final ClassificazioneCedolini classificazioneCedolini;
    private final NomeFileCedolinoStrategy nomeFile;
    private final RicostruzioneRapporto ricostruzione;
    private final ApplicationEventPublisher eventi;

    AcquisizioneCedolino(ScrittureSerializzate scritture, CedoliniRepository cedolini, CaricamentiRepository caricamenti,
                         CensimentoAnagrafica censimento, ConsultazioneAnagrafica consultazione,
                         CatalogoCcnl catalogoCcnl, ClassificazioneCedolini classificazioneCedolini,
                         NomeFileCedolinoStrategy nomeFile, RicostruzioneRapporto ricostruzione,
                         ApplicationEventPublisher eventi) {
        this.scritture = scritture;
        this.cedolini = cedolini;
        this.caricamenti = caricamenti;
        this.censimento = censimento;
        this.consultazione = consultazione;
        this.catalogoCcnl = catalogoCcnl;
        this.classificazioneCedolini = classificazioneCedolini;
        this.nomeFile = nomeFile;
        this.ricostruzione = ricostruzione;
        this.eventi = eventi;
    }

    /**
     * Documento caricato da archiviare.
     *
     * @param caricamentoId caricamento che lo archivia, null per un cedolino archiviato di nuovo con i dati modificati
     *                      a mano (i caricamenti che lo riguardano vengono ricollegati dal chiamante)
     * @param pdf         PDF da archiviare: quello caricato o, se era cifrato, la sua copia senza protezione
     * @param hash        hash SHA-256 del PDF caricato (anche quando si archivia la copia senza protezione)
     * @param pdfProtetto false se {@code pdf} e' senza password di apertura, null se non verificato
     */
    record DocumentoCaricato(WorkspaceId workspace, CaricamentoId caricamentoId, byte[] pdf, String hash,
                             Boolean pdfProtetto) {
    }

    /**
     * Esito della registrazione con il rapporto e la competenza determinati.
     *
     * @param rapporto   rapporto del cedolino (trovato o creato), null se scartato prima del censimento (file gia'
     *                   caricato)
     * @param competenza competenza definitiva del cedolino acquisito o quella in conflitto dello scartato, null se
     *                   scartato prima di determinarla
     */
    record Registrazione(EsitoAcquisizione esito, RapportoLavoro rapporto, Competenza competenza) {
    }

    /**
     * Esito di un inserimento a mano. Anagrafiche nuove, rapporti e associazione precedenti alla registrazione sono
     * rilevati solo nelle prove (altrimenti valgono false e null).
     *
     * @param nuovaPersona  la registrazione crea (o creerebbe) la persona: non e' stata scelta dall'elenco
     * @param nuovaAzienda  la registrazione crea (o creerebbe) l'azienda: non e' stata scelta dall'elenco
     * @param nuovoRapporto la registrazione crea (o creerebbe) il rapporto di lavoro
     * @param attribuzione  mensilita' a cui l'utente aveva associato il file, null se assente
     */
    record RegistrazioneManuale(Registrazione registrazione, boolean nuovaPersona, boolean nuovaAzienda,
                                boolean nuovoRapporto, AttribuzioneSalvata attribuzione) {
    }

    /**
     * Esito di una modifica a mano. Anagrafiche e rapporti nuovi sono rilevati solo nelle prove (altrimenti false).
     *
     * @param altroRapporto     il cedolino va (o andrebbe) a un rapporto diverso da quello attuale
     * @param rapportoEliminato il rapporto precedente del cedolino e' stato (o sarebbe) eliminato: era il suo unico
     *                          cedolino e ora va a un altro rapporto
     */
    record RegistrazioneModifica(Registrazione registrazione, boolean nuovaPersona, boolean nuovaAzienda,
                                 boolean nuovoRapporto, boolean altroRapporto, boolean rapportoEliminato) {
    }

    /**
     * Salva il cedolino estratto. Se acquisito, lo stato del caricamento diventa ACQUISITO nella stessa transazione;
     * se scartato, l'esito va registrato dal chiamante.
     *
     * @throws CedolinoNonAcquisibileException se mancano dati indispensabili
     * @throws RuntimeException                errori di censimento, database o ricalcolo (transazione annullata)
     */
    EsitoAcquisizione acquisisci(DocumentoCaricato documento, RisultatoEstrazione estrazione) {
        return scritture.inTransazione(documento.workspace(), stato -> {
            var esito = registra(documento, estrazione, true, Scelte.NESSUNA).esito();
            if (esito instanceof Scartato) {
                stato.setRollbackOnly();
            }
            return esito;
        });
    }

    /**
     * Inserimento a mano: registra il cedolino del file in errore a cui appartiene il caricamento, con il PDF originale
     * conservato del file e il suo hash, in una sola transazione serializzata. Prima della registrazione ricontrolla le
     * anagrafiche ({@link AnagraficheScelte}): quelle scelte devono essere ancora in archivio, quelle nuove non devono
     * esserci (un caricamento concorrente puo' averle eliminate o create). La transazione e' annullata se il cedolino e'
     * scartato e sempre nelle prove.
     *
     * @param prova  true per la prova: acquisizione completa poi annullata, senza log dell'acquisizione
     * @param scelte persona e azienda scelte dall'elenco; il cedolino ne riporta gia' i dati in archivio
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il caricamento non esiste nel workspace
     * @throws OperazioneNonConsentitaException                    se il file non e' piu' in errore, e' in
     *                                                             elaborazione o non ha il PDF conservato
     * @throws com.fcusumano.sam.shared.DatiNonValidiException     se un'anagrafica scelta non e' piu' in archivio o
     *                                                             una nuova lo e' (nulla viene scritto)
     * @throws CedolinoNonAcquisibileException                     se mancano dati indispensabili
     */
    RegistrazioneManuale acquisisciManuale(WorkspaceId workspace, CaricamentoId caricamentoId,
                                           RisultatoEstrazione estrazione, boolean prova, Scelte scelte) {
        return scritture.inTransazione(workspace, stato -> {
            var file = ControlliFileInErrore.daRisolvere(caricamenti, workspace, caricamentoId);
            var pdf = caricamenti.pdf(workspace, file.hash())
                    .orElseThrow(() -> new OperazioneNonConsentitaException(MessaggiCaricamento.PDF_NON_CONSERVATO));
            ricontrollaAnagrafiche(estrazione.cedolino(), scelte);
            Set<RapportoLavoroId> rapportiPrima = Set.of();
            AttribuzioneSalvata attribuzione = null;
            if (prova) {
                if (scelte.persona() != null) {
                    rapportiPrima = consultazione.rapportiDiPersona(scelte.persona()).stream()
                            .map(RapportoSintesi::id).collect(Collectors.toSet());
                }
                attribuzione = caricamenti.attribuzione(workspace, file.hash()).orElse(null);
            }
            // il PDF conservato puo' essere ancora protetto: lo verifica lo sblocco dei PDF archiviati
            var registrazione = registra(new DocumentoCaricato(workspace, caricamentoId, pdf, file.hash(), null),
                    estrazione, !prova, scelte);
            if (prova || registrazione.esito() instanceof Scartato) {
                stato.setRollbackOnly();
            }
            if (!prova) {
                return new RegistrazioneManuale(registrazione, false, false, false, null);
            }
            var rapporto = registrazione.rapporto();
            return new RegistrazioneManuale(registrazione, scelte.persona() == null, scelte.azienda() == null,
                    rapporto != null && !rapportiPrima.contains(rapporto.id()), attribuzione);
        });
    }

    /**
     * Modifica a mano: sostituisce il cedolino con i dati indicati, come se il suo PDF fosse caricato di nuovo con
     * questi dati, in una sola transazione serializzata. Il cedolino e' eliminato e il rapporto ricostruito dai
     * cedolini rimasti (come per un'eliminazione); poi il PDF archiviato, con il suo hash e il suo stato di protezione,
     * e' registrato con i dati modificati e i caricamenti che riguardavano il cedolino vengono ricollegati al nuovo. Se
     * il cedolino va a un altro rapporto, il precedente e' ricalcolato ({@link CedolinoEliminato}) o, rimasto senza
     * cedolini, eliminato con le anagrafiche orfane. La transazione e' annullata se il cedolino e' scartato (duplicato o
     * aggiuntivo di un altro) e sempre nelle prove.
     *
     * @param prova  true per la prova: modifica completa poi annullata, senza log
     * @param scelte persona e azienda scelte dall'elenco; il cedolino ne riporta gia' i dati in archivio
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il cedolino non esiste nel workspace
     * @throws com.fcusumano.sam.shared.DatiNonValidiException     se un'anagrafica scelta non e' piu' in archivio o
     *                                                             una nuova lo e' (nulla viene scritto)
     * @throws CedolinoNonAcquisibileException                     se mancano dati indispensabili
     */
    RegistrazioneModifica modificaManuale(WorkspaceId workspace, CedolinoId cedolinoId, RisultatoEstrazione estrazione,
                                          boolean prova, Scelte scelte) {
        return scritture.inTransazione(workspace, stato -> {
            var vecchio = cedolini.trova(workspace, cedolinoId)
                    .orElseThrow(() -> new RisorsaNonTrovataException("Cedolino", cedolinoId.valore()));
            var documento = cedolini.documento(workspace, cedolinoId)
                    .orElseThrow(() -> new IllegalStateException("PDF del cedolino " + cedolinoId.valore()
                            + " non trovato"));
            var precedente = consultazione.trovaRapporto(vecchio.rapportoId())
                    .orElseThrow(() -> new IllegalStateException("Rapporto del cedolino " + cedolinoId.valore()
                            + " non trovato"));
            ricontrollaAnagrafiche(estrazione.cedolino(), scelte);
            Set<RapportoLavoroId> rapportiPrima = Set.of();
            if (prova && scelte.persona() != null) {
                rapportiPrima = consultazione.rapportiDiPersona(scelte.persona()).stream()
                        .map(RapportoSintesi::id).collect(Collectors.toSet());
            }
            var riferimenti = caricamenti.riferimenti(workspace, cedolinoId);

            cedolini.elimina(workspace, cedolinoId);
            var rimasti = ricostruzione.normalizzati(workspace, precedente);
            if (rimasti.isEmpty()) {
                ricostruzione.svuota(precedente);
            } else {
                ricostruzione.ricostruisci(workspace, precedente, rimasti);
            }
            var registrazione = registra(new DocumentoCaricato(workspace, null, documento.pdf(), documento.hash(),
                    documento.pdfProtetto()), estrazione, !prova, scelte);
            if (prova || registrazione.esito() instanceof Scartato) {
                stato.setRollbackOnly();
            }
            var rapporto = registrazione.rapporto();
            boolean altroRapporto = rapporto != null && !rapporto.id().equals(precedente.id());
            if (registrazione.esito() instanceof Acquisito a) {
                caricamenti.ricollega(workspace, riferimenti, a.cedolinoId());
                if (altroRapporto) {
                    eventi.publishEvent(new CedolinoEliminato(cedolinoId, precedente.id(), vecchio.competenza()));
                    if (rimasti.isEmpty()) {
                        censimento.eliminaRapporto(precedente.id());
                    }
                }
                if (!prova) {
                    log.info("Cedolino {} modificato a mano: ora cedolino {} (rapporto {})", cedolinoId.valore(),
                            a.cedolinoId().valore(), rapporto.id().valore());
                }
            }
            boolean eliminato = altroRapporto && rimasti.isEmpty();
            if (!prova) {
                return new RegistrazioneModifica(registrazione, false, false, false, altroRapporto, eliminato);
            }
            return new RegistrazioneModifica(registrazione, scelte.persona() == null, scelte.azienda() == null,
                    altroRapporto && !rapportiPrima.contains(rapporto.id()), altroRapporto, eliminato);
        });
    }

    /**
     * Anagrafiche scelte ancora in archivio e anagrafiche nuove non in archivio, sui dati interpretati (quelli che il
     * censimento usera').
     *
     * @throws com.fcusumano.sam.shared.DatiNonValidiException altrimenti
     */
    private void ricontrollaAnagrafiche(CedolinoEstratto estratto, Scelte scelte) {
        AnagraficheScelte.risolvi(consultazione, scelte);
        var dipendente = estratto.dipendente();
        var datore = estratto.datoreLavoro();
        var errori = AnagraficheScelte.giaInArchivio(consultazione,
                dipendente == null ? null : dipendente.codiceFiscale(),
                datore == null ? null : new DatiAzienda(datore.codiceFiscale(), datore.partitaIva(),
                        datore.posizioneInps(), datore.ragioneSociale(), datore.indirizzo(), datore.codiceAzienda()),
                scelte);
        if (!errori.isEmpty()) {
            log.info("Inserimento a mano: anagrafica nuova gia' in archivio rilevata nella transazione");
            throw new DatiNonValidiException(errori);
        }
    }

    /**
     * Registra il cedolino nella transazione corrente (che il chiamante annulla se scartato).
     *
     * @param registraLog false nelle prove (transazione annullata): nessuna riga di log sull'acquisizione
     * @param scelte      anagrafiche scelte dall'elenco ({@link Scelte#NESSUNA} per i cedolini letti dai PDF)
     */
    private Registrazione registra(DocumentoCaricato documento, RisultatoEstrazione estrazione, boolean registraLog,
                                   Scelte scelte) {
        var ws = documento.workspace();
        var giaCaricato = cedolini.perHash(ws, documento.hash());
        if (giaCaricato.isPresent()) {
            return new Registrazione(
                    new Scartato(StatoCaricamento.DUPLICATO, MessaggiCaricamento.FILE_GIA_CARICATO, giaCaricato.get()),
                    null, null);
        }
        var estratto = estrazione.cedolino();
        var periodo = ConversioneCedolinoEstratto.periodo(estratto)
                .orElseThrow(() -> new CedolinoNonAcquisibileException(Dato.PERIODO,
                        MessaggiCaricamento.PERIODO_NON_LEGGIBILE));
        var tipo = ConversioneCedolinoEstratto.tipo(estratto);
        var rapporto = censisci(estratto, periodo, scelte);

        var assunzioneStampata = ConversioneCedolinoEstratto.dataAssunzione(estratto);
        var cessazioneStampata = ConversioneCedolinoEstratto.dataCessazione(estratto);
        var cessazione = cessazioneStampata != null ? cessazioneStampata : rapporto.dataCessazione();
        var determinata = DeterminazioneCompetenza.determina(tipo, periodo, assunzioneStampata, cessazione);
        Competenza chiave = switch (determinata) {
            case Mensile m -> m.competenza();
            case DiFineRapporto f -> new Competenza.FineRapporto(1, f.periodo());
        };
        var esistente = cedolini.perChiave(ws, rapporto.id(), chiave);
        if (tipo == TipoDichiarato.AGGIUNTIVA) {
            return new Registrazione(new Scartato(StatoCaricamento.AGGIUNTIVO_NON_GESTITO,
                    MessaggiCaricamento.aggiuntivo(chiave), esistente.map(CedolinoConImporti::id).orElse(null)),
                    rapporto, chiave);
        }
        if (esistente.isPresent()) {
            return new Registrazione(conflitto(esistente.get(), estratto), rapporto, chiave);
        }

        // Riclassificazione dei cedolini gia' archiviati rispetto alla cessazione del rapporto (aggiornata dal
        // censimento) e progressivo TFR definitivo del nuovo: il risultato non dipende dall'ordine di caricamento
        var classificazione = classificazioneCedolini.riclassifica(ws, rapporto, rapporto.dataCessazione(),
                new CedolinoInArrivo(tipo, periodo, assunzioneStampata, chiave));
        var competenza = classificazione.competenzaInArrivo();

        var dati = ConversioneCedolinoEstratto.normalizzato(estratto, competenza, periodo, rapporto);
        var id = cedolini.inserisci(ws, new NuovoCedolino(
                dati,
                tipo,
                nomeFile.nomeFile(competenza, rapporto.aziendaId(), rapporto.personaId()),
                documento.hash(),
                documento.pdf(),
                documento.pdfProtetto(),
                estrazione.jsonOriginale(),
                estratto.confidenza() == null ? null : estratto.confidenza().complessiva(),
                estratto.campiNonTrovati(),
                estrazione.avvisi(),
                estrazione.modello()));
        var estremi = EstremiCedolini.di(cedolini.periodi(ws, rapporto.id()));
        censimento.aggiornaEstremiCedolini(rapporto.id(), estremi.primo(), estremi.ultimo());
        // il file non e' piu' in errore: caricamento, PDF conservato e associazione si aggiornano prima degli eventi,
        // cosi' i listener sincroni (segnalazioni) vedono uno stato coerente
        if (documento.caricamentoId() != null) {
            caricamenti.aggiorna(ws, documento.caricamentoId(), StatoCaricamento.ACQUISITO, null, id, null);
            // tentativi in errore dello stesso file conclusi dopo questo (es. lo stesso PDF due volte nel lotto,
            // inserito a mano dal primo): ora sono duplicati, cosi' eliminando il cedolino il file non torna in errore
            caricamenti.chiudiErroriSuccessivi(ws, documento.hash(), documento.caricamentoId(), id,
                    MessaggiCaricamento.FILE_GIA_CARICATO);
        }
        caricamenti.eliminaPdf(ws, documento.hash());
        var associato = caricamenti.eliminaAttribuzione(ws, documento.hash());
        eventi.publishEvent(new CedolinoAcquisito(id, rapporto.id(), competenza));
        associato.filter(r -> !r.equals(rapporto.id()))
                .ifPresent(r -> eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(r))));
        if (registraLog) {
            if (documento.caricamentoId() != null) {
                log.info("Acquisito cedolino {} (caricamento {}, rapporto {}, competenza {})", id.valore(),
                        documento.caricamentoId().valore(), rapporto.id().valore(), competenza.codice());
            } else {
                log.info("Archiviato di nuovo il cedolino {} con i dati modificati (rapporto {}, competenza {})",
                        id.valore(), rapporto.id().valore(), competenza.codice());
            }
            if (classificazione.modificati() > 0) {
                log.info("Riclassificati o rinumerati {} cedolini del rapporto {}", classificazione.modificati(),
                        rapporto.id().valore());
            }
        }
        return new Registrazione(new Acquisito(id), rapporto, competenza);
    }

    /**
     * Persona, azienda e rapporto del cedolino. Una persona scelta e' ritrovata per il suo codice fiscale (gia' quello
     * in archivio, univoco nel workspace); un'azienda scelta e' collegata per id, senza abbinamento.
     *
     * @throws IllegalStateException se il rapporto non e' quello delle anagrafiche scelte (difetto: mai atteso)
     */
    private RapportoLavoro censisci(CedolinoEstratto estratto, YearMonth periodo, Scelte scelte) {
        var dipendente = estratto.dipendente();
        if (dipendente == null || dipendente.codiceFiscale() == null) {
            throw new CedolinoNonAcquisibileException(Dato.CODICE_FISCALE,
                    MessaggiCaricamento.CODICE_FISCALE_NON_LEGGIBILE);
        }
        var datore = estratto.datoreLavoro();
        if (datore == null || datore.ragioneSociale() == null) {
            throw new CedolinoNonAcquisibileException(Dato.DATORE, MessaggiCaricamento.DATORE_NON_LEGGIBILE);
        }
        var persona = censimento.trovaOCreaPersona(new DatiPersona(dipendente.codiceFiscale(), dipendente.nome(),
                dipendente.cognome(), ConversioneCedolinoEstratto.dataNascita(estratto)));
        var datiAzienda = new DatiAzienda(datore.codiceFiscale(), datore.partitaIva(), datore.posizioneInps(),
                datore.ragioneSociale(), datore.indirizzo(), datore.codiceAzienda(), periodo);
        var azienda = scelte.azienda() != null
                ? censimento.aggiornaAziendaScelta(scelte.azienda(), datiAzienda)
                : censimento.trovaOCreaAzienda(datiAzienda);
        var contratto = estratto.datiContrattuali();
        String ccnlTesto = contratto == null ? null : contratto.ccnl();
        String ccnl = ccnlTesto == null ? null : catalogoCcnl.riconosci(ccnlTesto).map(Ccnl::codice).orElse(null);
        var rapporto = censimento.trovaOCreaRapporto(persona.id(), azienda.id(), new DatiRapporto(
                ConversioneCedolinoEstratto.dataAssunzione(estratto),
                ConversioneCedolinoEstratto.dataCessazione(estratto),
                contratto == null ? null : contratto.qualifica(),
                contratto == null ? null : contratto.livello(),
                dipendente.codiceDipendente(),
                ccnl,
                periodo));
        if ((scelte.persona() != null && !scelte.persona().equals(rapporto.personaId()))
                || (scelte.azienda() != null && !scelte.azienda().equals(rapporto.aziendaId()))) {
            throw new IllegalStateException("Rapporto diverso dalle anagrafiche scelte");
        }
        return rapporto;
    }

    private static Scartato conflitto(CedolinoConImporti esistente, CedolinoEstratto estratto) {
        var totali = estratto.totali();
        var nuovi = totali == null ? new ImportiChiave(null, null, null, null)
                : new ImportiChiave(totali.netto(), totali.lordo() != null ? totali.lordo() : totali.totaleCompetenze(),
                totali.totaleCompetenze(), totali.totaleTrattenute());
        if (ConfrontoCedolini.stessoContenuto(esistente.importi(), nuovi)) {
            return new Scartato(StatoCaricamento.DUPLICATO, MessaggiCaricamento.duplicato(esistente.competenza()),
                    esistente.id());
        }
        return new Scartato(StatoCaricamento.AGGIUNTIVO_NON_GESTITO,
                MessaggiCaricamento.aggiuntivoConImportiDiversi(esistente.competenza()), esistente.id());
    }
}
