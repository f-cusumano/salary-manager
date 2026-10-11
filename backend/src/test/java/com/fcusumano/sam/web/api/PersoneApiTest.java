package com.fcusumano.sam.web.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PersoneApiTest extends ApiWebMvcTest {

    @Test
    void elencoVuotoPrimaDeiCaricamenti() throws Exception {
        when(anagrafica.elencoPersone()).thenReturn(List.of());

        mvc.perform(get("/api/v1/persone"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void etichetteDellePersoneConCodiceFiscaleSoloPerGliOmonimi() throws Exception {
        when(anagrafica.elencoPersone()).thenReturn(List.of(
                new PersonaSintesi(new PersonaId(4), "Laura", "Bianchi", "BNCLRA85M41F205X", LocalDate.of(1985, 8, 1),
                        "Laura Bianchi"),
                new PersonaSintesi(new PersonaId(1), "Mario", "Rossi", "RSSMRA80A01H501U", null,
                        "Mario Rossi (RSSMRA80A01H501U)"),
                new PersonaSintesi(new PersonaId(2), "Mario", "Rossi", "RSSMRA85C12F205M", LocalDate.of(1985, 3, 12),
                        "Mario Rossi (RSSMRA85C12F205M)")));

        mvc.perform(get("/api/v1/persone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].etichetta").value(contains("Laura Bianchi",
                        "Mario Rossi (RSSMRA80A01H501U)", "Mario Rossi (RSSMRA85C12F205M)")))
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].nome").value("Laura"))
                .andExpect(jsonPath("$[0].cognome").value("Bianchi"))
                .andExpect(jsonPath("$[0].codiceFiscale").value("BNCLRA85M41F205X"))
                .andExpect(jsonPath("$[0].dataNascita").value("1985-08-01"))
                .andExpect(jsonPath("$[1]").value(hasKey("dataNascita")))
                .andExpect(jsonPath("$[1].dataNascita").value(nullValue()));
    }

    @Test
    void rapportiInOrdineConEtichetteAntiOmonimiaEPeriodo() throws Exception {
        var gammaMilano = new AziendaId(5);
        var gammaRoma = new AziendaId(6);
        when(anagrafica.rapportiDiPersona(new PersonaId(1))).thenReturn(List.of(
                new RapportoSintesi(new RapportoLavoroId(11), new PersonaId(1), gammaMilano, "Gamma Servizi S.r.l.",
                        "09876543217", "Gamma Servizi S.r.l. (P.IVA 09876543217)", DatiDiProva.periodoAttuale(),
                        null),
                new RapportoSintesi(new RapportoLavoroId(10), new PersonaId(1), gammaRoma, "Gamma Servizi S.r.l.",
                        null, "Gamma Servizi S.r.l. (Pos. INPS 7012345678)", DatiDiProva.periodoConcluso(),
                        "COMMERCIO")));
        when(anagrafica.trovaAzienda(gammaMilano)).thenReturn(Optional.of(new Azienda(
                gammaMilano, null, "09876543217", null, "Gamma Servizi S.r.l.", "Via Verdi 2, Milano", null)));
        when(anagrafica.trovaAzienda(gammaRoma)).thenReturn(Optional.empty());
        when(segnalazioni.haSegnalazioni(new RapportoLavoroId(11))).thenReturn(true);

        mvc.perform(get("/api/v1/persone/1/rapporti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].personaId").value(1))
                .andExpect(jsonPath("$[0].etichetta").value("Gamma Servizi S.r.l. (P.IVA 09876543217)"))
                .andExpect(jsonPath("$[0].azienda.indirizzo").value("Via Verdi 2, Milano"))
                .andExpect(jsonPath("$[0].periodo.da").value("2023-01"))
                .andExpect(jsonPath("$[0].periodo.a").value(nullValue()))
                .andExpect(jsonPath("$[0].periodo.fonteFine").value("NON_DISPONIBILE"))
                .andExpect(jsonPath("$[0].attuale").value(true))
                .andExpect(jsonPath("$[0].haSegnalazioni").value(true))
                .andExpect(jsonPath("$[0].ccnlDaIndicare").value(true))
                .andExpect(jsonPath("$[1].etichetta").value("Gamma Servizi S.r.l. (Pos. INPS 7012345678)"))
                .andExpect(jsonPath("$[1].azienda.id").value(6))
                .andExpect(jsonPath("$[1].azienda.ragioneSociale").value("Gamma Servizi S.r.l."))
                .andExpect(jsonPath("$[1].periodo.a").value("2024-06"))
                .andExpect(jsonPath("$[1].attuale").value(false))
                .andExpect(jsonPath("$[1].haSegnalazioni").value(false))
                .andExpect(jsonPath("$[1].ccnlDaIndicare").value(false));
    }

    @Test
    void personaInesistente404() throws Exception {
        when(anagrafica.rapportiDiPersona(new PersonaId(99))).thenThrow(new RisorsaNonTrovataException("Persona", 99L));

        mvc.perform(get("/api/v1/persone/99/rapporti"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Risorsa non trovata"))
                .andExpect(jsonPath("$.type").value("https://salary-manager.local/problemi/risorsa-non-trovata"))
                .andExpect(jsonPath("$.detail").value("Persona inesistente (id 99)."));
    }

    @Test
    void identificativoNonValido400() throws Exception {
        mvc.perform(get("/api/v1/persone/0/rapporti"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Messaggi.ID_NON_VALIDO));
        mvc.perform(get("/api/v1/persone/abc/rapporti"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Richiesta non valida"))
                .andExpect(jsonPath("$.detail").value("Valore non valido per \"personaId\"."));
        verifyNoInteractions(anagrafica);
    }

    @Test
    void endpointInesistente404EMetodoNonConsentito405() throws Exception {
        mvc.perform(get("/api/v1/inesistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Risorsa inesistente."));
        mvc.perform(delete("/api/v1/persone"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Metodo DELETE non consentito per questa risorsa."));
    }
}
