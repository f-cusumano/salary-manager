package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiRapporto;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.domain.DatiContrattualiCorrenti;
import com.fcusumano.sam.cedolini.domain.EstremiCedolini;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stato del rapporto ricostruito dai cedolini rimasti dopo che uno e' stato tolto (eliminazione o modifica a mano):
 * dati contrattuali dell'ultimo cedolino, riclassificazione rispetto alla nuova cessazione con rinumerazione dei
 * progressivi TFR e dei nomi file, estremi dei cedolini. Nella transazione del chiamante.
 */
@Component
class RicostruzioneRapporto {

    private static final Logger log = LoggerFactory.getLogger(RicostruzioneRapporto.class);

    private static final Comparator<CedolinoNormalizzato> NORMALIZZATI_CRONOLOGICI =
            Comparator.comparing(CedolinoNormalizzato::competenza, Competenza.ORDINE_CRONOLOGICO);

    private final CedoliniRepository cedolini;
    private final CensimentoAnagrafica censimento;
    private final CatalogoCcnl catalogoCcnl;
    private final ClassificazioneCedolini classificazioneCedolini;

    RicostruzioneRapporto(CedoliniRepository cedolini, CensimentoAnagrafica censimento, CatalogoCcnl catalogoCcnl,
                          ClassificazioneCedolini classificazioneCedolini) {
        this.cedolini = cedolini;
        this.censimento = censimento;
        this.catalogoCcnl = catalogoCcnl;
        this.classificazioneCedolini = classificazioneCedolini;
    }

    /** Cedolini normalizzati del rapporto in ordine cronologico crescente. */
    List<CedolinoNormalizzato> normalizzati(WorkspaceId ws, RapportoLavoro rapporto) {
        return cedolini.normalizzati(ws, rapporto.id(), rapporto.personaId(), rapporto.aziendaId()).stream()
                .sorted(NORMALIZZATI_CRONOLOGICI)
                .toList();
    }

    /** Ricostruisce il rapporto dai cedolini rimasti (almeno uno). */
    void ricostruisci(WorkspaceId ws, RapportoLavoro rapporto, List<CedolinoNormalizzato> rimasti) {
        var correnti = DatiContrattualiCorrenti.da(rimasti);
        var ccnl = correnti.ccnlTesto() == null ? null
                : catalogoCcnl.riconosci(correnti.ccnlTesto()).map(Ccnl::codice).orElse(null);
        var aggiornato = censimento.sovrascriviDatiRapporto(rapporto.id(), new DatiRapporto(correnti.dataAssunzione(),
                correnti.dataCessazione(), correnti.qualifica(), correnti.livello(), correnti.codiceDipendente(), ccnl,
                correnti.riferimento()));
        var classificazione = classificazioneCedolini.riclassifica(ws, aggiornato, aggiornato.dataCessazione(), null);
        if (classificazione.modificati() > 0) {
            log.info("Riclassificati o rinumerati {} cedolini del rapporto {}", classificazione.modificati(),
                    rapporto.id().valore());
        }
        // Estremi dopo la riclassificazione: dipendono dalle mensilita' ordinarie
        var estremi = EstremiCedolini.di(cedolini.periodi(ws, rapporto.id()));
        censimento.aggiornaEstremiCedolini(rapporto.id(), estremi.primo(), estremi.ultimo());
    }

    /**
     * Rapporto rimasto senza cedolini ma non ancora eliminato (modifica a mano): dati contrattuali ed estremi azzerati,
     * li riportera' il cedolino archiviato di nuovo se resta in questo rapporto. Il CCNL scelto dall'utente resta.
     */
    void svuota(RapportoLavoro rapporto) {
        censimento.sovrascriviDatiRapporto(rapporto.id(), new DatiRapporto(null, null, null, null, null, null, null));
        censimento.aggiornaEstremiCedolini(rapporto.id(), null, null);
    }
}
