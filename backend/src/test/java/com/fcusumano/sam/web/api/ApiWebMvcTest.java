package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dei test MockMvc dello strato REST: tutto il modulo web (controller, composizione, gestione errori,
 * sicurezza, OpenAPI) con le API pubbliche degli altri moduli sostituite da mock. Contesto condiviso tra le
 * sottoclassi.
 */
@WebMvcTest(includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.fcusumano\\.sam\\.web\\..*"))
@ActiveProfiles("test")
abstract class ApiWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ConsultazioneAnagrafica anagrafica;

    @MockitoBean
    GestioneRapporti gestioneRapporti;

    @MockitoBean
    CatalogoCcnl catalogoCcnl;

    @MockitoBean
    ArchivioCedolini archivio;

    @MockitoBean
    CalcoliRapporto calcoli;

    @MockitoBean
    SegnalazioniRapporto segnalazioni;

    @MockitoBean
    CaricamentoCedolini caricamento;

    @MockitoBean
    CaricamentiInErrore caricamentiInErrore;
}
