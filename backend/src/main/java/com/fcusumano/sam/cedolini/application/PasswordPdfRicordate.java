package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.shared.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Password dei PDF protetti indicate dall'utente che hanno aperto almeno un PDF: ricordate cifrate
 * ({@link CifraturaPassword}) e provate sui PDF protetti successivi. Una password non piu' decifrabile (chiave cambiata)
 * viene ignorata. Mai loggate.
 */
@Component
class PasswordPdfRicordate {

    private static final Logger log = LoggerFactory.getLogger(PasswordPdfRicordate.class);

    private final PasswordPdfRepository repository;
    private final CifraturaPassword cifratura;
    private final ScrittureSerializzate scritture;

    PasswordPdfRicordate(PasswordPdfRepository repository, CifraturaPassword cifratura,
                         ScrittureSerializzate scritture) {
        this.repository = repository;
        this.cifratura = cifratura;
        this.scritture = scritture;
    }

    /** Password ricordate del workspace, in chiaro, dalla prima ricordata. */
    List<String> elenco(WorkspaceId workspace) {
        var password = new ArrayList<String>();
        int illeggibili = 0;
        for (var cifrata : repository.cifrate(workspace)) {
            try {
                password.add(cifratura.decifra(workspace, cifrata));
            } catch (IllegalArgumentException e) {
                illeggibili++;
            }
        }
        if (illeggibili > 0) {
            log.warn("{} password ricordate dei PDF non decifrabili con la chiave attuale: ignorate", illeggibili);
        }
        return password;
    }

    /**
     * Ricorda la password se non e' gia' ricordata, nella transazione serializzata del workspace (due file dello stesso
     * lotto con la stessa password non la salvano due volte).
     *
     * @return true se la password e' nuova
     */
    boolean ricorda(WorkspaceId workspace, String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        return scritture.inTransazione(workspace, stato -> {
            if (elenco(workspace).contains(password)) {
                return false;
            }
            repository.inserisci(workspace, cifratura.cifra(workspace, password));
            log.info("Ricordata una nuova password dei PDF protetti");
            return true;
        });
    }
}
