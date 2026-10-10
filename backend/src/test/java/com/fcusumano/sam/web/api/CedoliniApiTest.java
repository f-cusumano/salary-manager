package com.fcusumano.sam.web.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CedoliniApiTest extends ApiWebMvcTest {

    private static final CedolinoId CEDOLINO = new CedolinoId(16);
    private static final byte[] PDF = "%PDF-1.7\n% cedolino di prova\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void dettaglioPerAreeTematiche() throws Exception {
        when(archivio.dettaglio(CEDOLINO)).thenReturn(Optional.of(dettaglio()));

        mvc.perform(get("/api/v1/cedolini/16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(16))
                .andExpect(jsonPath("$.rapportoId").value(7))
                .andExpect(jsonPath("$.titolo").value("Gennaio 2024"))
                .andExpect(jsonPath("$.competenza.chiave").value("2024.01"))
                .andExpect(jsonPath("$.periodoRetribuzione").value("2024-01"))
                .andExpect(jsonPath("$.meseVariabili").value("2023-12"))
                .andExpect(jsonPath("$.nomeFile").value("2024.01_3_1.pdf"))
                .andExpect(jsonPath("$.acquisitoIl").value("2025-03-10T09:30:00Z"))
                .andExpect(jsonPath("$.confidenza").value(0.95))
                .andExpect(jsonPath("$.campiNonTrovati[0]").value("codiceDipendente"))
                .andExpect(jsonPath("$.avvisiEstrazione[0]").value("Partita IVA dell'azienda formalmente non valida"))
                .andExpect(jsonPath("$.inquadramento.ccnlTesto").value("CCNL Commercio"))
                .andExpect(jsonPath("$.inquadramento.dataAssunzione").value("2023-01-09"))
                .andExpect(jsonPath("$.totali.netto").value(1810.0))
                .andExpect(jsonPath("$.totali.arrotondamento").value(-0.42))
                .andExpect(jsonPath("$.elementiRetributivi.superminimoAssorbibile").value(true))
                .andExpect(jsonPath("$.elementiRetributivi.divisoreGiornaliero").value(26))
                .andExpect(jsonPath("$.presenze.giorniLavorati").value(21))
                .andExpect(jsonPath("$.presenze.voci[0].descrizione").value("Ore ordinarie"))
                .andExpect(jsonPath("$.competenze[0].descrizione").value("Retribuzione"))
                .andExpect(jsonPath("$.competenze[0].importoCompetenza").value(2350.0))
                .andExpect(jsonPath("$.competenze[0].quantita").value(168))
                .andExpect(jsonPath("$.trattenutePrevidenziali[0].descrizione").value("Contributi IVS"))
                .andExpect(jsonPath("$.trattenuteFiscali[0].descrizione").value("Ritenuta IRPEF"))
                .andExpect(jsonPath("$.altreTrattenute", empty()))
                .andExpect(jsonPath("$.vociNonRetributive[0].descrizione").value("Rimborso chilometrico"))
                .andExpect(jsonPath("$.vociInformative", empty()))
                .andExpect(jsonPath("$.tfr.quotaMese").value(180.0))
                .andExpect(jsonPath("$.tfr.liquidatoLordo").value(nullValue()))
                .andExpect(jsonPath("$.ratei", hasSize(1)))
                .andExpect(jsonPath("$.ratei[0].tipo").value("ROL"))
                .andExpect(jsonPath("$.ratei[0].unita").value("ORE"))
                .andExpect(jsonPath("$.ratei[0].saldo").value(16.5))
                .andExpect(jsonPath("$.scatti.numeroScatti").value(1))
                .andExpect(jsonPath("$.scatti.prossimoScatto").value("2026-02-01"))
                .andExpect(jsonPath("$.buoniPasto.presenti").value(true))
                .andExpect(jsonPath("$.buoniPasto.numero").value(20))
                .andExpect(jsonPath("$.progressivi.imponibileIrpef").value(nullValue()))
                .andExpect(jsonPath("$.contieneTredicesima").value(false));
    }

    @Test
    void dettaglioDiUnCedolinoInesistente404() throws Exception {
        when(archivio.dettaglio(new CedolinoId(99))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/cedolini/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cedolino inesistente (id 99)."));
    }

    @Test
    void eliminazione204() throws Exception {
        mvc.perform(delete("/api/v1/cedolini/16"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(archivio).elimina(CEDOLINO);
    }

    @Test
    void eliminazioneDiUnCedolinoInesistente404() throws Exception {
        doThrow(new RisorsaNonTrovataException("Cedolino", 99L)).when(archivio).elimina(new CedolinoId(99));

        mvc.perform(delete("/api/v1/cedolini/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cedolino inesistente (id 99)."));
    }

    @Test
    void eliminazioneInConflitto409() throws Exception {
        doThrow(new OperazioneNonConsentitaException("Caricamento in corso sul rapporto: riprova al termine."))
                .when(archivio).elimina(CEDOLINO);

        mvc.perform(delete("/api/v1/cedolini/16"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Caricamento in corso sul rapporto: riprova al termine."));
    }

    @Test
    void eliminazioneConIdentificativoNonValido400() throws Exception {
        mvc.perform(delete("/api/v1/cedolini/-3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Messaggi.ID_NON_VALIDO));
        verify(archivio, never()).elimina(any());
    }

    @Test
    void scaricaIlPdfConIlNomeNormalizzato() throws Exception {
        when(archivio.pdf(CEDOLINO)).thenReturn(Optional.of(new PdfCedolino("2024.01_3_1.pdf", PDF)));

        mvc.perform(get("/api/v1/cedolini/16/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"2024.01_3_1.pdf\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''2024.01_3_1.pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void ilNomeDelFileDiFineRapportoEValidoInContentDisposition() throws Exception {
        when(archivio.pdf(CEDOLINO)).thenReturn(Optional.of(new PdfCedolino("2020.TFR01_3_1.pdf", PDF)));

        mvc.perform(get("/api/v1/cedolini/16/pdf").accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"2020.TFR01_3_1.pdf\"; filename*=UTF-8''2020.TFR01_3_1.pdf"));
    }

    @Test
    void pdfInesistente404AncheChiedendoUnPdf() throws Exception {
        when(archivio.pdf(new CedolinoId(99))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/cedolini/99/pdf").accept(MediaType.APPLICATION_PDF, MediaType.ALL))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Cedolino inesistente (id 99)."));
    }

    @Test
    void corsEsponeContentDispositionAlFrontendDiSviluppo() throws Exception {
        when(archivio.pdf(CEDOLINO)).thenReturn(Optional.of(new PdfCedolino("2024.01_3_1.pdf", PDF)));

        mvc.perform(get("/api/v1/cedolini/16/pdf").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.CONTENT_DISPOSITION)));
        mvc.perform(options("/api/v1/cedolini/16")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("DELETE")));
        mvc.perform(get("/api/v1/cedolini/16/pdf").header(HttpHeaders.ORIGIN, "http://sito-estraneo.example"))
                .andExpect(status().isForbidden());
    }

    private static CedolinoDettaglio dettaglio() {
        var dati = new CedolinoNormalizzato(
                CEDOLINO, new RapportoLavoroId(7), new PersonaId(1), new AziendaId(3),
                Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1)), YearMonth.of(2024, 1), YearMonth.of(2023, 12),
                LocalDate.of(2024, 1, 31), LocalDate.of(2023, 1, 9), null, "Impiegato", "3", null, "CCNL Commercio",
                new BigDecimal("2400.00000"), new BigDecimal("2400.00000"), new BigDecimal("589.58000"),
                new BigDecimal("-0.42000"), new BigDecimal("1810.00000"),
                new ElementiRetributivi(new BigDecimal("2000.00000"), null, new BigDecimal("300.00000"), true,
                        new BigDecimal("50.00000"), null, null, new BigDecimal("2350.00000"), null,
                        new BigDecimal("90.38462"), null, new BigDecimal("26.00000")),
                List.of(
                        new Voce("0001", "Retribuzione", CategoriaVoce.COMPETENZA, new BigDecimal("168.00000"), "ore",
                                null, new BigDecimal("2350.00000"), null),
                        new Voce(null, "Ore ordinarie", CategoriaVoce.PRESENZA, new BigDecimal("168.00000"), "ore",
                                null, null, null),
                        new Voce("Z001", "Contributi IVS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, null, null,
                                new BigDecimal("2400.00000"), null, new BigDecimal("220.32000")),
                        new Voce("F001", "Ritenuta IRPEF", CategoriaVoce.TRATTENUTA_FISCALE, null, null, null, null,
                                new BigDecimal("369.26000")),
                        new Voce("R010", "Rimborso chilometrico", CategoriaVoce.NON_RETRIBUTIVA,
                                new BigDecimal("120.00000"), "km", new BigDecimal("0.42000"),
                                new BigDecimal("50.40000"), null)),
                List.of(new Rateo(TipoRateo.ROL, UnitaMisura.ORE, new BigDecimal("8.00000"), new BigDecimal("8.50000"),
                        null, new BigDecimal("16.50000"))),
                new DatiTfr(new BigDecimal("4000.00000"), null, null, null, new BigDecimal("180.00000"), null, null,
                        null, null, null, null),
                new Presenze(new BigDecimal("21.00000"), new BigDecimal("168.00000"), null),
                1, LocalDate.of(2026, 2, 1),
                new BuoniPasto(true, new BigDecimal("20.00000"), new BigDecimal("8.00000"),
                        new BigDecimal("160.00000")),
                false, false,
                new ProgressiviAnnui(new BigDecimal("2400.00000"), null, null));
        return new CedolinoDettaglio(dati, "2024.01_3_1.pdf", DatiDiProva.ISTANTE, new BigDecimal("0.95"),
                List.of("codiceDipendente"), List.of("Partita IVA dell'azienda formalmente non valida"));
    }
}
