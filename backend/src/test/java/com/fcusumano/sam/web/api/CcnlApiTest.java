package com.fcusumano.sam.web.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.ccnl.Ccnl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

class CcnlApiTest extends ApiWebMvcTest {

    @Test
    void elencoSenzaParametri() throws Exception {
        when(catalogoCcnl.elenco()).thenReturn(List.of(DatiDiProva.COMMERCIO,
                new Ccnl("TURISMO", "Turismo", 14, 48, 6, null, "Mese della quattordicesima da verificare")));

        mvc.perform(get("/api/v1/ccnl"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"codice": "COMMERCIO", "descrizione": "Commercio e terziario", "mensilita": 14,
                           "periodicitaScattiMesi": 36, "maxScatti": 10, "meseQuattordicesima": 7, "noteVerifica": null},
                          {"codice": "TURISMO", "descrizione": "Turismo", "mensilita": 14, "periodicitaScattiMesi": 48,
                           "maxScatti": 6, "meseQuattordicesima": null,
                           "noteVerifica": "Mese della quattordicesima da verificare"}
                        ]
                        """, JsonCompareMode.STRICT));
    }
}
