package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caricamento asincrono dei cedolini: registra i file IN_CODA in una transazione breve (committata prima di avviare
 * l'elaborazione) e li passa a {@link ElaborazioneCaricamenti}. Ritorna subito.
 */
@Service
class CaricamentoCedoliniService implements CaricamentoCedolini {

    private static final Logger log = LoggerFactory.getLogger(CaricamentoCedoliniService.class);

    /** Lunghezza massima del nome file originale (colonna {@code caricamento.nome_file_originale}). */
    static final int LUNGHEZZA_NOME_FILE = 255;
    static final String NOME_FILE_PREDEFINITO = "documento.pdf";
    /** Massimo numero di caricamenti restituiti da {@link #recenti(int)}. */
    static final int LIMITE_RECENTI = 500;

    private final CaricamentiRepository caricamenti;
    private final ElaborazioneCaricamenti elaborazione;
    private final CurrentWorkspaceProvider workspaceProvider;
    private final ConsultazioneAnagrafica anagrafica;
    private final TransactionTemplate nuovaTransazione;

    CaricamentoCedoliniService(CaricamentiRepository caricamenti, ElaborazioneCaricamenti elaborazione,
                               CurrentWorkspaceProvider workspaceProvider, ConsultazioneAnagrafica anagrafica,
                               PlatformTransactionManager transactionManager) {
        this.caricamenti = caricamenti;
        this.elaborazione = elaborazione;
        this.workspaceProvider = workspaceProvider;
        this.anagrafica = anagrafica;
        this.nuovaTransazione = new TransactionTemplate(transactionManager);
        this.nuovaTransazione.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<Caricamento> avvia(List<FileCaricato> files, String password) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        var ws = workspaceProvider.corrente();
        var lotto = UUID.randomUUID();
        var inCoda = new ArrayList<FileInCoda>(files.size());
        for (var file : files) {
            inCoda.add(new FileInCoda(nomeVisualizzato(file.nomeFileOriginale()), file.contenuto().length,
                    sha256(file.contenuto())));
        }
        var registrati = nuovaTransazione.execute(stato -> caricamenti.registra(ws, lotto, inCoda));
        var passwordLotto = password == null || password.isEmpty() ? null : password;
        var lavori = new ArrayList<Lavoro>(registrati.size());
        for (int i = 0; i < registrati.size(); i++) {
            lavori.add(new Lavoro(ws, registrati.get(i).id(), inCoda.get(i).nomeFileOriginale(),
                    files.get(i).contenuto(), inCoda.get(i).hash(), passwordLotto));
        }
        elaborazione.accoda(lavori);
        log.info("Lotto {} avviato con {} file", lotto, registrati.size());
        return registrati;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Caricamento> statoLotto(UUID lotto) {
        return conPersona(caricamenti.perLotto(workspaceProvider.corrente(), lotto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Caricamento> stato(Collection<CaricamentoId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return conPersona(caricamenti.perId(workspaceProvider.corrente(), ids));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Caricamento> recenti(int limite) {
        if (limite <= 0) {
            return List.of();
        }
        return conPersona(caricamenti.recenti(workspaceProvider.corrente(), Math.min(limite, LIMITE_RECENTI)));
    }

    /** Completa i caricamenti attribuiti a un rapporto con la persona del rapporto (una lettura per rapporto). */
    private List<Caricamento> conPersona(List<Caricamento> letti) {
        var persone = new HashMap<RapportoLavoroId, PersonaId>();
        return letti.stream().map(c -> c.rapportoId() == null ? c : conPersona(c, persone)).toList();
    }

    private Caricamento conPersona(Caricamento c, Map<RapportoLavoroId, PersonaId> persone) {
        var personaId = persone.computeIfAbsent(c.rapportoId(),
                id -> anagrafica.trovaRapporto(id).map(RapportoLavoro::personaId).orElse(null));
        return new Caricamento(c.id(), c.lotto(), c.nomeFileOriginale(), c.dimensione(), c.stato(), c.messaggio(),
                c.motivo(), c.pdfDisponibile(), c.cedolinoId(), c.cedolinoEsistenteId(), personaId,
                personaId == null ? null : c.rapportoId(), c.creatoIl(), c.aggiornatoIl());
    }

    /** Nome del file senza percorso (alcuni browser lo includono), mai vuoto, entro la lunghezza della colonna. */
    static String nomeVisualizzato(String nomeFileOriginale) {
        if (nomeFileOriginale == null) {
            return NOME_FILE_PREDEFINITO;
        }
        var percorso = nomeFileOriginale.replace('\\', '/');
        var nome = percorso.substring(percorso.lastIndexOf('/') + 1).strip();
        if (nome.isEmpty()) {
            return NOME_FILE_PREDEFINITO;
        }
        return nome.length() <= LUNGHEZZA_NOME_FILE ? nome : nome.substring(nome.length() - LUNGHEZZA_NOME_FILE);
    }

    static String sha256(byte[] contenuto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenuto));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
