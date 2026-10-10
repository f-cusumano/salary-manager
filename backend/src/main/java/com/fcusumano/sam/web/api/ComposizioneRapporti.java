package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.AziendaSintesiDto;
import com.fcusumano.sam.web.api.dto.PersonaSintesiDto;
import com.fcusumano.sam.web.api.dto.RapportoDettaglioDto;
import com.fcusumano.sam.web.api.dto.RapportoSintesiDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compone le viste dei rapporti a partire dalle API pubbliche di anagrafica, CCNL, cedolini, calcoli e segnalazioni.
 * Se calcoli e segnalazioni non hanno ancora dati per il rapporto, il dettaglio ha riepilogo null e liste vuote.
 * Le letture avvengono in un'unica transazione di sola lettura, per una vista coerente.
 */
@Component
@Transactional(readOnly = true)
class ComposizioneRapporti {

    static final String RAPPORTO = "Rapporto di lavoro";

    private final ConsultazioneAnagrafica anagrafica;
    private final CatalogoCcnl catalogoCcnl;
    private final ArchivioCedolini archivio;
    private final CalcoliRapporto calcoli;
    private final SegnalazioniRapporto segnalazioni;

    ComposizioneRapporti(ConsultazioneAnagrafica anagrafica, CatalogoCcnl catalogoCcnl, ArchivioCedolini archivio,
                         CalcoliRapporto calcoli, SegnalazioniRapporto segnalazioni) {
        this.anagrafica = anagrafica;
        this.catalogoCcnl = catalogoCcnl;
        this.archivio = archivio;
        this.calcoli = calcoli;
        this.segnalazioni = segnalazioni;
    }

    /**
     * Rapporti della persona nell'ordine stabilito dall'anagrafica (cronologico inverso, attuale per primo).
     *
     * @throws RisorsaNonTrovataException se la persona non esiste
     */
    List<RapportoSintesiDto> rapportiDiPersona(long personaId) {
        return anagrafica.rapportiDiPersona(new PersonaId(personaId)).stream().map(this::sintesi).toList();
    }

    private RapportoSintesiDto sintesi(RapportoSintesi r) {
        var azienda = anagrafica.trovaAzienda(r.aziendaId())
                .map(Mappatura::azienda)
                .orElseGet(() -> new AziendaSintesiDto(r.aziendaId().valore(), r.ragioneSociale(), r.partitaIva(),
                        null, null, null));
        return new RapportoSintesiDto(r.id().valore(), r.personaId().valore(), azienda, r.etichettaAzienda(),
                Mappatura.periodo(r.periodo()), r.periodo().attuale(), segnalazioni.haSegnalazioni(r.id()),
                r.ccnlCodice() == null);
    }

    /**
     * Dettaglio completo del rapporto (3 aree).
     *
     * @throws RisorsaNonTrovataException se il rapporto non esiste
     */
    RapportoDettaglioDto dettaglio(long rapportoId) {
        var id = new RapportoLavoroId(rapportoId);
        var dettaglio = anagrafica.dettaglioRapporto(id)
                .orElseThrow(() -> new RisorsaNonTrovataException(RAPPORTO, rapportoId));
        var rapporto = dettaglio.rapporto();
        var attuale = dettaglio.periodo().attuale();
        var ccnl = Optional.ofNullable(rapporto.ccnlCodice()).flatMap(catalogoCcnl::trova);
        var riepilogo = calcoli.riepilogo(id);
        var mensilita = ListaMensilita.componi(archivio.sintesiDiRapporto(id), segnalazioni.mensilitaMancanti(id),
                riepilogo.map(ComposizioneRapporti::indicatoriPerCedolino).orElse(Map.of()),
                ccnl.map(Ccnl::meseQuattordicesima).orElse(null));
        return new RapportoDettaglioDto(
                rapportoId,
                persona(dettaglio.persona()),
                Mappatura.azienda(dettaglio.azienda()),
                dettaglio.etichettaAzienda(),
                Mappatura.periodo(dettaglio.periodo()),
                attuale,
                rapporto.dataAssunzione(),
                rapporto.dataCessazione(),
                rapporto.qualifica(),
                rapporto.livello(),
                ccnl.map(Mappatura::ccnl).orElse(null),
                ccnl.isEmpty(),
                rapporto.ccnlSceltoDaUtente(),
                rapporto.note(),
                segnalazioni.segnalazioni(id).stream().map(Mappatura::segnalazione).toList(),
                riepilogo.map(r -> Mappatura.riepilogo(r, attuale)).orElse(null),
                mensilita);
    }

    /** La persona con l'etichetta anti-omonimia calcolata dall'anagrafica sull'intero workspace. */
    private PersonaSintesiDto persona(Persona persona) {
        return anagrafica.elencoPersone().stream()
                .filter(p -> p.id().equals(persona.id()))
                .findFirst()
                .map(Mappatura::persona)
                .orElseGet(() -> new PersonaSintesiDto(persona.id().valore(), persona.nome(), persona.cognome(),
                        persona.codiceFiscale(), persona.nomeCompleto()));
    }

    private static Map<CedolinoId, IndicatoriMensilita> indicatoriPerCedolino(RiepilogoRapporto riepilogo) {
        return riepilogo.mensilita().stream()
                .collect(Collectors.toMap(IndicatoriMensilita::cedolinoId, Function.identity(), (a, b) -> b));
    }
}
