package com.fcusumano.sam.web.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Protezione dal DNS rebinding: le API rispondono solo ai nomi host ammessi (localhost e loopback per default);
 * per un altro nome host la richiesta e' rifiutata con 400 prima di arrivare ai controller.
 */
class NomiHostAmmessiTest extends ApiWebMvcTest {

    @Test
    void richiestaConUnNomeHostEstraneoRifiutata() throws Exception {
        mvc.perform(get("/api/v1/persone").header("Host", "attaccante.example:8080"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Richiesta non valida"));
    }

    @Test
    void caricamentoConUnNomeHostEstraneoRifiutato() throws Exception {
        var file = new MockMultipartFile("files", "cedolino.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1});

        mvc.perform(multipart("/api/v1/caricamenti").file(file)
                        .header("Host", "attaccante.example:8080")
                        .header("Origin", "http://attaccante.example:8080"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localhostELoopbackAmmessi() throws Exception {
        when(anagrafica.elencoPersone()).thenReturn(List.of());

        for (var host : List.of("localhost:8080", "LOCALHOST", "127.0.0.1:8081", "[::1]:8080")) {
            mvc.perform(get("/api/v1/persone").header("Host", host)).andExpect(status().isOk());
        }
    }
}
