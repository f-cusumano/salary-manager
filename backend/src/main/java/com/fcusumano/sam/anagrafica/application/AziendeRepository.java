package com.fcusumano.sam.anagrafica.application;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.domain.ProfiloAzienda;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porta di persistenza delle aziende (tabella {@code azienda}).
 */
public interface AziendeRepository {

    Optional<ProfiloAzienda> trova(WorkspaceId workspace, AziendaId id);

    List<ProfiloAzienda> trova(WorkspaceId workspace, Collection<AziendaId> ids);

    /**
     * Aziende che hanno almeno una corrispondenza con i valori indicati: codice fiscale o partita IVA tra
     * {@code identificativiFiscali} (confronto incrociato), stessa matricola INPS o stessa ragione sociale normalizzata.
     * I parametri null o vuoti sono ignorati.
     */
    List<ProfiloAzienda> candidate(WorkspaceId workspace, Set<String> identificativiFiscali, String posizioneInps,
                                   String ragioneSocialeNormalizzata);

    /** Valori tra quelli indicati gia' usati come codice fiscale o partita IVA da aziende diverse da {@code esclusa}. */
    Set<String> identificativiFiscaliUsati(WorkspaceId workspace, Set<String> valori, AziendaId esclusa);

    AziendaId inserisci(WorkspaceId workspace, ProfiloAzienda azienda);

    void aggiorna(WorkspaceId workspace, ProfiloAzienda azienda);

    void elimina(WorkspaceId workspace, AziendaId id);
}
