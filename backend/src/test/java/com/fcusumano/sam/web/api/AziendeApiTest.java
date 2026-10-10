package com.fcusumano.sam.web.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.AziendaSelezionabile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AziendeApiTest extends ApiWebMvcTest {

    @Test
    void elencoVuotoPrimaDeiCaricamenti() throws Exception {
        when(anagrafica.elencoAziende()).thenReturn(List.of());

        mvc.perform(get("/api/v1/aziende"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void aziendeConTuttiIDatiDaPrecompilareEdEtichetteAntiOmonimia() throws Exception {
        when(anagrafica.elencoAziende()).thenReturn(List.of(
                new AziendaSelezionabile(DatiDiProva.azienda(), "Alfa Software S.r.l."),
                new AziendaSelezionabile(new Azienda(new AziendaId(5), null, "09876543217", null,
                        "Gamma Servizi S.r.l.", "Via Verdi 2, Milano", null),
                        "Gamma Servizi S.r.l. (P.IVA 09876543217)"),
                new AziendaSelezionabile(new Azienda(new AziendaId(6), null, null, "7012345678",
                        "Gamma Servizi S.r.l.", null, null), "Gamma Servizi S.r.l. (Pos. INPS 7012345678)")));

        mvc.perform(get("/api/v1/aziende"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].etichetta").value(contains("Alfa Software S.r.l.",
                        "Gamma Servizi S.r.l. (P.IVA 09876543217)", "Gamma Servizi S.r.l. (Pos. INPS 7012345678)")))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].ragioneSociale").value("Alfa Software S.r.l."))
                .andExpect(jsonPath("$[0].partitaIva").value("01234567897"))
                .andExpect(jsonPath("$[0].posizioneInps").value("1122334455"))
                .andExpect(jsonPath("$[0].indirizzo").value("Via Roma 1, Milano"))
                .andExpect(jsonPath("$[0].codiceAzienda").value("A01"))
                // i dati mancanti ci sono, a null: il modulo li lascia vuoti da compilare
                .andExpect(jsonPath("$[0]").value(hasKey("codiceFiscale")))
                .andExpect(jsonPath("$[0].codiceFiscale").value(nullValue()))
                .andExpect(jsonPath("$[2].id").value(6))
                .andExpect(jsonPath("$[2].partitaIva").value(nullValue()))
                .andExpect(jsonPath("$[2].indirizzo").value(nullValue()))
                .andExpect(jsonPath("$[2].codiceAzienda").value(nullValue()));
    }

    @Test
    void lElencoESoloInLettura() throws Exception {
        mvc.perform(post("/api/v1/aziende").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(anagrafica);
    }
}
