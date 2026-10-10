package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Porta di persistenza dello stato dei caricamenti (tabella {@code caricamento}). I caricamenti letti riportano il
 * rapporto del cedolino creato o gia' presente, ma non la persona: la risolve il servizio tramite l'anagrafica.
 */
public interface CaricamentiRepository {

    /** Registra i file del lotto come IN_CODA, nell'ordine indicato. */
    List<Caricamento> registra(WorkspaceId workspace, UUID lotto, List<FileInCoda> files);

    /** Aggiorna stato, messaggio e riferimenti ai cedolini. */
    void aggiorna(WorkspaceId workspace, CaricamentoId id, StatoCaricamento stato, String messaggio,
                  CedolinoId cedolinoId, CedolinoId cedolinoEsistenteId);

    List<Caricamento> perLotto(WorkspaceId workspace, UUID lotto);

    List<Caricamento> perId(WorkspaceId workspace, Collection<CaricamentoId> ids);

    List<Caricamento> recenti(WorkspaceId workspace, int limite);

    /**
     * Porta in ERRORE con il messaggio indicato i caricamenti di tutti i workspace ancora IN_CODA o IN_LETTURA.
     *
     * @return numero di caricamenti aggiornati
     */
    int interrompiNonTerminati(String messaggio);

    /** File da registrare: nome come caricato, dimensione in byte, hash SHA-256 esadecimale. */
    record FileInCoda(String nomeFileOriginale, long dimensione, String hash) {
    }
}
