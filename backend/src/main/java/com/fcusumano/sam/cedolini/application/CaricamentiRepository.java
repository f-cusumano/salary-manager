package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta di persistenza dello stato dei caricamenti (tabella {@code caricamento}), del PDF originale conservato dei
 * file in errore ({@code caricamento_pdf}) e della mensilita' a cui l'utente li associa
 * ({@code caricamento_attribuzione}). Un file e' identificato da workspace e hash del contenuto: e' in errore se il suo
 * ultimo tentativo concluso e' in ERRORE e nessun cedolino archiviato ha quell'hash. I caricamenti letti riportano il
 * rapporto del cedolino creato o gia' presente, ma non la persona: la risolve il servizio tramite l'anagrafica.
 */
public interface CaricamentiRepository {

    /** Registra i file del lotto come IN_CODA, nell'ordine indicato. */
    List<Caricamento> registra(WorkspaceId workspace, UUID lotto, List<FileInCoda> files);

    /**
     * Aggiorna stato, messaggio e riferimenti ai cedolini; azzera il motivo dell'errore (gli errori si registrano con
     * {@link #registraErrore}).
     */
    void aggiorna(WorkspaceId workspace, CaricamentoId id, StatoCaricamento stato, String messaggio,
                  CedolinoId cedolinoId, CedolinoId cedolinoEsistenteId);

    /** Porta il caricamento in ERRORE con motivo e messaggio (troncato alla lunghezza della colonna). */
    void registraErrore(WorkspaceId workspace, CaricamentoId id, MotivoErrore motivo, String messaggio);

    List<Caricamento> perLotto(WorkspaceId workspace, UUID lotto);

    List<Caricamento> perId(WorkspaceId workspace, Collection<CaricamentoId> ids);

    List<Caricamento> recenti(WorkspaceId workspace, int limite);

    /**
     * Porta in ERRORE con motivo e messaggio indicati i caricamenti di tutti i workspace ancora IN_CODA o IN_LETTURA.
     * Il PDF conservato del file non cambia.
     *
     * @return numero di caricamenti aggiornati
     */
    int interrompiNonTerminati(MotivoErrore motivo, String messaggio);

    /** Conserva il PDF originale del file, se non e' gia' conservato (stesso hash = stesso contenuto). */
    void salvaPdfSeAssente(WorkspaceId workspace, String hash, byte[] contenuto);

    /** Elimina il PDF conservato del file, se presente. */
    void eliminaPdf(WorkspaceId workspace, String hash);

    /** PDF originale conservato del file. */
    Optional<byte[]> pdf(WorkspaceId workspace, String hash);

    /**
     * Elimina l'associazione del file a una mensilita', se presente.
     *
     * @return rapporto della mensilita' a cui il file era associato
     */
    Optional<RapportoLavoroId> eliminaAttribuzione(WorkspaceId workspace, String hash);

    /**
     * Elimina, in tutti i workspace, PDF conservati e associazioni di file che non sono piu' in errore (rete di
     * sicurezza all'avvio: di norma spariscono nella transazione che risolve il file).
     *
     * @return numero di righe eliminate
     */
    int ripulisciFileNonInErrore();

    /** File da registrare: nome come caricato, dimensione in byte, hash SHA-256 esadecimale. */
    record FileInCoda(String nomeFileOriginale, long dimensione, String hash) {
    }
}
