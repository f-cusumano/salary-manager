package com.fcusumano.sam.ccnl.application;

import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.domain.RiconoscitoreCcnl;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementazione di {@link CatalogoCcnl}: letture della tabella e riconoscimento conservativo dal testo stampato.
 * La tabella e' piccola: le regole di riconoscimento vengono lette a ogni richiesta.
 */
@Service
@Transactional(readOnly = true)
class CatalogoCcnlService implements CatalogoCcnl {

    private static final Logger log = LoggerFactory.getLogger(CatalogoCcnlService.class);

    private final ArchivioCcnl archivio;

    CatalogoCcnlService(ArchivioCcnl archivio) {
        this.archivio = archivio;
    }

    @Override
    public List<Ccnl> elenco() {
        return archivio.attivi();
    }

    @Override
    public Optional<Ccnl> trova(String codice) {
        if (codice == null || codice.isBlank()) {
            return Optional.empty();
        }
        return archivio.perCodice(codice.strip());
    }

    @Override
    public Optional<Ccnl> riconosci(String testoStampato) {
        if (testoStampato == null || testoStampato.isBlank()) {
            return Optional.empty();
        }
        var riconosciuto = new RiconoscitoreCcnl(archivio.regoleRiconoscimento()).riconosci(testoStampato)
                .flatMap(archivio::perCodice);
        log.debug("Riconoscimento CCNL dal testo stampato: {}", riconosciuto.map(Ccnl::codice).orElse("nessuno"));
        return riconosciuto;
    }
}
