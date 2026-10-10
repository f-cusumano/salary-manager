package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoroDettaglio;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.AttribuzioneCaricamento;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.web.api.dto.AttribuzioneCaricamentoDto;
import com.fcusumano.sam.web.api.dto.CaricamentoInErroreDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compone i file in errore per la UI: alla mensilita' associata aggiunge persona e azienda del rapporto (etichette
 * anti-omonimia dell'anagrafica) e il titolo della mensilita'. Solo letture, in una transazione di sola lettura: le
 * azioni sui file passano direttamente dall'API del modulo cedolini.
 */
@Component
@Transactional(readOnly = true)
class ComposizioneCaricamentiInErrore {

    private final CaricamentiInErrore caricamentiInErrore;
    private final ConsultazioneAnagrafica anagrafica;

    ComposizioneCaricamentiInErrore(CaricamentiInErrore caricamentiInErrore, ConsultazioneAnagrafica anagrafica) {
        this.caricamentiInErrore = caricamentiInErrore;
        this.anagrafica = anagrafica;
    }

    /** File in errore del workspace, uno per file, dal piu' recente. */
    List<CaricamentoInErroreDto> elenco() {
        var etichette = new Etichette();
        return caricamentiInErrore.elenco().stream().map(c -> dto(c, etichette)).toList();
    }

    /** Un file in errore (risposta delle azioni di associazione). */
    CaricamentoInErroreDto dto(CaricamentoInErrore caricamento) {
        return dto(caricamento, new Etichette());
    }

    private static CaricamentoInErroreDto dto(CaricamentoInErrore caricamento, Etichette etichette) {
        var attribuzione = Optional.ofNullable(caricamento.attribuzione()).flatMap(etichette::attribuzione);
        return Mappatura.caricamentoInErrore(caricamento, attribuzione.orElse(null));
    }

    /**
     * Letture anagrafiche di una composizione, fatte una volta sola: persone del workspace (per le etichette) e
     * dettaglio di ogni rapporto associato.
     */
    private final class Etichette {

        private Map<PersonaId, String> persone;
        private final Map<RapportoLavoroId, Optional<RapportoLavoroDettaglio>> rapporti = new HashMap<>();

        /** Vuota se il rapporto non esiste piu' (l'associazione e' eliminata con il rapporto). */
        Optional<AttribuzioneCaricamentoDto> attribuzione(AttribuzioneCaricamento a) {
            return rapporti.computeIfAbsent(a.rapportoId(), anagrafica::dettaglioRapporto).map(dettaglio -> {
                var persona = dettaglio.persona();
                return new AttribuzioneCaricamentoDto(a.rapportoId().valore(), persona.id().valore(),
                        persone().getOrDefault(persona.id(), persona.nomeCompleto()), dettaglio.etichettaAzienda(),
                        Mappatura.competenza(a.competenza()), Titoli.di(a.competenza()), a.competenzaPresente());
            });
        }

        private Map<PersonaId, String> persone() {
            if (persone == null) {
                persone = anagrafica.elencoPersone().stream()
                        .collect(Collectors.toMap(PersonaSintesi::id, PersonaSintesi::etichetta, (a, b) -> a));
            }
            return persone;
        }
    }
}
