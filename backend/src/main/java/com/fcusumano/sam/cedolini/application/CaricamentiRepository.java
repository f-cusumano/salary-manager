package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * Porta in ERRORE con il motivo indicato i caricamenti di tutti i workspace ancora IN_CODA o IN_LETTURA. Il PDF
     * conservato del file non cambia; il messaggio dipende dalla sua presenza (con il PDF il file si rilegge o si
     * inserisce a mano, senza va caricato di nuovo).
     *
     * @param messaggioConPdf   messaggio dei caricamenti il cui file ha il PDF conservato
     * @param messaggioSenzaPdf messaggio degli altri
     * @return numero di caricamenti aggiornati
     */
    int interrompiNonTerminati(MotivoErrore motivo, String messaggioConPdf, String messaggioSenzaPdf);

    /**
     * Porta in DUPLICATO, con il messaggio indicato e il riferimento al cedolino esistente, i caricamenti di tutti i
     * workspace ancora IN_CODA o IN_LETTURA il cui file e' gia' archiviato come cedolino (la loro elaborazione li
     * avrebbe scartati: interromperli renderebbe il file di nuovo "in errore" se il cedolino venisse eliminato).
     *
     * @return numero di caricamenti aggiornati
     */
    int scartaNonTerminatiGiaArchiviati(String messaggio);

    /**
     * Rapporti (con il loro workspace) a cui sono associati i file con un caricamento IN_CODA o IN_LETTURA: il
     * ripristino ne cambia il motivo dell'errore, che le segnalazioni riportano.
     */
    Set<RapportoAssociato> rapportiAssociatiANonTerminati();

    /** Rapporti (con il loro workspace) a cui sono associati file che non sono piu' in errore. */
    Set<RapportoAssociato> rapportiAssociatiAFileNonInErrore();

    /**
     * Chiude come DUPLICATO, con il messaggio indicato e il riferimento al cedolino appena archiviato, i tentativi in
     * ERRORE dello stesso file successivi (per id) al caricamento acquisito: altrimenti, eliminato il cedolino, il
     * file tornerebbe "in errore" (il suo ultimo tentativo concluso sarebbe un ERRORE).
     *
     * @return numero di caricamenti aggiornati
     */
    int chiudiErroriSuccessivi(WorkspaceId workspace, String hash, CaricamentoId acquisito, CedolinoId cedolino,
                               String messaggio);

    /** Caricamenti che fanno riferimento al cedolino (acquisito, o gia' presente per un duplicato o un aggiuntivo). */
    RiferimentiCedolino riferimenti(WorkspaceId workspace, CedolinoId cedolino);

    /**
     * Ricollega al cedolino indicato i caricamenti che facevano riferimento a un cedolino sostituito (modifica a mano:
     * il cedolino e' eliminato e archiviato di nuovo, con un nuovo id).
     */
    void ricollega(WorkspaceId workspace, RiferimentiCedolino riferimenti, CedolinoId cedolino);

    /** Il file e' in errore: ultimo tentativo concluso in ERRORE e nessun cedolino archiviato con quell'hash. */
    boolean fileInErrore(WorkspaceId workspace, String hash);

    /** Conserva il PDF originale del file, se non e' gia' conservato (stesso hash = stesso contenuto). */
    void salvaPdfSeAssente(WorkspaceId workspace, String hash, byte[] contenuto);

    /**
     * Conserva la copia senza protezione del PDF del file (l'hash resta quello del PDF caricato), al posto dell'eventuale
     * PDF protetto gia' conservato.
     */
    void salvaPdfSbloccato(WorkspaceId workspace, String hash, byte[] contenuto);

    /** Elimina il PDF conservato del file, se presente. */
    void eliminaPdf(WorkspaceId workspace, String hash);

    /** PDF conservato del file: come caricato o, se una password lo ha aperto, la sua copia senza protezione. */
    Optional<byte[]> pdf(WorkspaceId workspace, String hash);

    /**
     * Elimina l'associazione del file a una mensilita', se presente.
     *
     * @return rapporto della mensilita' a cui il file era associato
     */
    Optional<RapportoLavoroId> eliminaAttribuzione(WorkspaceId workspace, String hash);

    /** Associazione del file a una mensilita'. */
    Optional<AttribuzioneSalvata> attribuzione(WorkspaceId workspace, String hash);

    /** Associa il file alla mensilita' del rapporto (il file non deve essere gia' associato). */
    void attribuisci(WorkspaceId workspace, String hash, RapportoLavoroId rapportoId, Competenza.Mensilita competenza);

    /** Hash del file associato alla mensilita' del rapporto (al piu' uno). */
    Optional<String> hashAssociatoA(WorkspaceId workspace, RapportoLavoroId rapportoId, Competenza.Mensilita competenza);

    /**
     * Elimina, in tutti i workspace, PDF conservati e associazioni di file che non sono piu' in errore (rete di
     * sicurezza all'avvio: di norma spariscono nella transazione che risolve il file).
     *
     * @return numero di righe eliminate
     */
    int ripulisciFileNonInErrore();

    /**
     * File in errore del workspace, uno per file (la riga e' il suo ultimo tentativo concluso, in ERRORE), dal piu'
     * recente, con PDF conservato, elaborazione in corso e associazione.
     */
    List<CaricamentoInErrore> inErrore(WorkspaceId workspace);

    /** Come {@link #inErrore(WorkspaceId)}, solo i file associati a una mensilita' del rapporto. */
    List<CaricamentoInErrore> inErroreDiRapporto(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Il file con l'hash indicato, se in errore. */
    Optional<CaricamentoInErrore> inErrore(WorkspaceId workspace, String hash);

    /** Caricamento con lo stato del suo file. */
    Optional<FileDelCaricamento> fileDelCaricamento(WorkspaceId workspace, CaricamentoId id);

    /**
     * Elimina i tentativi in ERRORE del file (gli altri tentativi restano).
     *
     * @return numero di caricamenti eliminati
     */
    int eliminaErroriDelFile(WorkspaceId workspace, String hash);

    /** File da registrare: nome come caricato, dimensione in byte, hash SHA-256 esadecimale. */
    /**
     * @param acquisiti caricamenti che hanno archiviato il cedolino
     * @param esistenti caricamenti scartati come duplicato o aggiuntivo del cedolino
     */
    record RiferimentiCedolino(List<CaricamentoId> acquisiti, List<CaricamentoId> esistenti) {

        public RiferimentiCedolino {
            acquisiti = List.copyOf(acquisiti);
            esistenti = List.copyOf(esistenti);
        }
    }

    record FileInCoda(String nomeFileOriginale, long dimensione, String hash) {
    }

    /** Rapporto a cui e' associato un file in errore, con il suo workspace. */
    record RapportoAssociato(WorkspaceId workspace, RapportoLavoroId rapportoId) {
    }

    /** Mensilita' a cui l'utente ha associato un file in errore. */
    record AttribuzioneSalvata(RapportoLavoroId rapportoId, Competenza.Mensilita competenza) {
    }

    /**
     * Un caricamento e lo stato del suo file (workspace + hash).
     *
     * @param stato          stato del caricamento indicato
     * @param inErrore       il file e' in errore (ultimo tentativo concluso in ERRORE, contenuto non archiviato)
     * @param inElaborazione un tentativo del file e' IN_CODA o IN_LETTURA
     * @param pdfPresente    il PDF originale del file e' conservato
     */
    record FileDelCaricamento(StatoCaricamento stato, String nomeFileOriginale, long dimensione, String hash,
                              boolean inErrore, boolean inElaborazione, boolean pdfPresente) {
    }
}
