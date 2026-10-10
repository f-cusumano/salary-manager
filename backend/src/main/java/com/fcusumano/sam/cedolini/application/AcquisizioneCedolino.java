package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.DatiPersona;
import com.fcusumano.sam.anagrafica.DatiRapporto;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.StatoCaricamento;
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
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.YearMonth;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Fase di persistenza di un caricamento, in una transazione serializzata per workspace: censimento di persona,
 * azienda e rapporto, determinazione della competenza, controllo di unicita' (duplicato / aggiuntivo), salvataggio
 * di cedolino e PDF originale, riclassificazione dei cedolini del rapporto e rinumerazione dei progressivi TFR
 * ({@link ClassificazioneCedolini}), estremi del rapporto, caricamento ACQUISITO, eliminazione di PDF conservato e
 * associazione del file (non piu' in errore) e infine pubblicazione di {@link CedolinoAcquisito} (i ricalcoli dei
 * moduli calcoli e segnalazioni avvengono nella stessa transazione e vedono uno stato gia' coerente).
 * Un cedolino scartato annulla la transazione: non resta alcuna anagrafica creata per lui.
 * Nei log compaiono solo id.
 */
@Component
class AcquisizioneCedolino {

    private static final Logger log = LoggerFactory.getLogger(AcquisizioneCedolino.class);

    private final ScrittureSerializzate scritture;
    private final CedoliniRepository cedolini;
    private final CaricamentiRepository caricamenti;
    private final CensimentoAnagrafica censimento;
    private final CatalogoCcnl catalogoCcnl;
    private final ClassificazioneCedolini classificazioneCedolini;
    private final NomeFileCedolinoStrategy nomeFile;
    private final ApplicationEventPublisher eventi;

    AcquisizioneCedolino(ScrittureSerializzate scritture, CedoliniRepository cedolini, CaricamentiRepository caricamenti,
                         CensimentoAnagrafica censimento, CatalogoCcnl catalogoCcnl,
                         ClassificazioneCedolini classificazioneCedolini,
                         NomeFileCedolinoStrategy nomeFile, ApplicationEventPublisher eventi) {
        this.scritture = scritture;
        this.cedolini = cedolini;
        this.caricamenti = caricamenti;
        this.censimento = censimento;
        this.catalogoCcnl = catalogoCcnl;
        this.classificazioneCedolini = classificazioneCedolini;
        this.nomeFile = nomeFile;
        this.eventi = eventi;
    }

    /** Documento caricato da archiviare: PDF originale (come caricato) e suo hash SHA-256. */
    record DocumentoCaricato(WorkspaceId workspace, CaricamentoId caricamentoId, byte[] pdfOriginale, String hash) {
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
     * Salva il cedolino estratto. Se acquisito, lo stato del caricamento diventa ACQUISITO nella stessa transazione;
     * se scartato, l'esito va registrato dal chiamante.
     *
     * @throws CedolinoNonAcquisibileException se mancano dati indispensabili
     * @throws RuntimeException                errori di censimento, database o ricalcolo (transazione annullata)
     */
    EsitoAcquisizione acquisisci(DocumentoCaricato documento, RisultatoEstrazione estrazione) {
        return scritture.inTransazione(documento.workspace(), stato -> {
            var esito = registra(documento, estrazione, true).esito();
            if (esito instanceof Scartato) {
                stato.setRollbackOnly();
            }
            return esito;
        });
    }

    /**
     * Registra il cedolino nella transazione corrente (che il chiamante annulla se scartato).
     *
     * @param registraLog false nelle prove (transazione annullata): nessuna riga di log sull'acquisizione
     */
    private Registrazione registra(DocumentoCaricato documento, RisultatoEstrazione estrazione, boolean registraLog) {
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
        var rapporto = censisci(estratto, periodo);

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
                documento.pdfOriginale(),
                estrazione.jsonOriginale(),
                estratto.confidenza() == null ? null : estratto.confidenza().complessiva(),
                estratto.campiNonTrovati(),
                estrazione.avvisi(),
                estrazione.modello()));
        var estremi = EstremiCedolini.di(cedolini.periodi(ws, rapporto.id()));
        censimento.aggiornaEstremiCedolini(rapporto.id(), estremi.primo(), estremi.ultimo());
        // il file non e' piu' in errore: caricamento, PDF conservato e associazione si aggiornano prima degli eventi,
        // cosi' i listener sincroni (segnalazioni) vedono uno stato coerente
        caricamenti.aggiorna(ws, documento.caricamentoId(), StatoCaricamento.ACQUISITO, null, id, null);
        caricamenti.eliminaPdf(ws, documento.hash());
        var associato = caricamenti.eliminaAttribuzione(ws, documento.hash());
        eventi.publishEvent(new CedolinoAcquisito(id, rapporto.id(), competenza));
        associato.filter(r -> !r.equals(rapporto.id()))
                .ifPresent(r -> eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(r))));
        if (registraLog) {
            log.info("Acquisito cedolino {} (caricamento {}, rapporto {}, competenza {})", id.valore(),
                    documento.caricamentoId().valore(), rapporto.id().valore(), competenza.codice());
            if (classificazione.modificati() > 0) {
                log.info("Riclassificati o rinumerati {} cedolini del rapporto {}", classificazione.modificati(),
                        rapporto.id().valore());
            }
        }
        return new Registrazione(new Acquisito(id), rapporto, competenza);
    }

    private RapportoLavoro censisci(CedolinoEstratto estratto, YearMonth periodo) {
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
        var azienda = censimento.trovaOCreaAzienda(new DatiAzienda(datore.codiceFiscale(), datore.partitaIva(),
                datore.posizioneInps(), datore.ragioneSociale(), datore.indirizzo(), datore.codiceAzienda(), periodo));
        var contratto = estratto.datiContrattuali();
        String ccnlTesto = contratto == null ? null : contratto.ccnl();
        String ccnl = ccnlTesto == null ? null : catalogoCcnl.riconosci(ccnlTesto).map(Ccnl::codice).orElse(null);
        return censimento.trovaOCreaRapporto(persona.id(), azienda.id(), new DatiRapporto(
                ConversioneCedolinoEstratto.dataAssunzione(estratto),
                ConversioneCedolinoEstratto.dataCessazione(estratto),
                contratto == null ? null : contratto.qualifica(),
                contratto == null ? null : contratto.livello(),
                dipendente.codiceDipendente(),
                ccnl,
                periodo));
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
