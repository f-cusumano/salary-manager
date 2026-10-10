package com.fcusumano.sam.web.api;

import static com.fcusumano.sam.web.api.DatiDiProva.ISTANTE;
import static com.fcusumano.sam.web.api.DatiDiProva.LOTTO_IN_ERRORE;
import static com.fcusumano.sam.web.api.DatiDiProva.fileInErrore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import com.fcusumano.sam.web.api.dto.RichiestaRiletturaDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Cedolini caricati con errore via REST (modulo cedolini simulato): codici di stato delle 8 operazioni, errori campo
 * per campo, messaggi dei conflitti, PDF originale, intestazioni Location e riservatezza della password. Dati inventati.
 */
@ExtendWith(OutputCaptureExtension.class)
class CaricamentiInErroreApiTest extends ApiWebMvcTest {

    private static final CaricamentoId CARICAMENTO = new CaricamentoId(40);
    private static final RapportoLavoroId RAPPORTO = new RapportoLavoroId(7);
    private static final Competenza.Mensilita MAGGIO = Competenza.Mensilita.ordinaria(YearMonth.of(2023, 5));
    private static final UUID NUOVO_LOTTO = UUID.fromString("0c9d8e7f-6a5b-4c3d-9e2f-1a0b9c8d7e6f");
    private static final String PASSWORD = "Segreto-Rilettura-77";
    private static final byte[] PDF = "%PDF-1.4\n% file in errore di prova\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String NON_IN_ERRORE = "Il file non è più in errore: aggiorna l'elenco";

    /** Cedolino inserito a mano di prova (dati inventati), nel formato JSON del contratto. */
    private static final String CEDOLINO = """
            {
              "dipendente": {"codiceFiscale": "RSSMRA80A01H501U", "nome": "Mario", "cognome": "Rossi",
                             "dataNascita": "1980-01-01", "codiceDipendente": "D001"},
              "datoreLavoro": {"codiceFiscale": null, "partitaIva": "IT01234567897", "posizioneInps": "1122334455",
                               "posizioneInail": null, "ragioneSociale": "Alfa Software S.r.l.",
                               "indirizzo": "Via Roma 1, Milano", "codiceAzienda": "A01"},
              "datiContrattuali": {"dataAssunzione": "2023-01-09", "dataCessazione": null, "qualifica": "Impiegato",
                                   "livello": "3", "ccnl": "CCNL Commercio", "codiceCnel": "H011"},
              "periodo": {"anno": 2023, "mese": 5, "tipo": "ORDINARIA", "dataElaborazione": "2023-05-31",
                          "meseVariabili": "2023-04"},
              "totali": {"lordo": 2400.00, "totaleCompetenze": 2400.00, "totaleTrattenute": 589.58,
                         "arrotondamento": -0.42, "netto": 1810.00},
              "retribuzione": {"minimo": 2000.00, "contingenza": null, "superminimo": 300.00,
                               "superminimoAssorbibile": true, "importoScatti": 50.00, "altriElementiFissi": null,
                               "retribuzioneBase": null, "totale": 2350.00, "pagaOraria": null,
                               "pagaGiornaliera": 90.38462, "divisoreOrario": null, "divisoreGiornaliero": 26},
              "voci": [
                {"codice": "0001", "descrizione": "Retribuzione", "categoria": "COMPETENZA", "quantita": 168,
                 "unita": "ore", "base": null, "importoCompetenza": 2350.00, "importoTrattenuta": null},
                {"codice": null, "descrizione": "Ritenuta IRPEF", "categoria": "TRATTENUTA_FISCALE", "quantita": null,
                 "unita": null, "base": null, "importoCompetenza": null, "importoTrattenuta": 369.26}
              ],
              "ratei": [{"tipo": "FERIE", "unita": "GIORNI", "residuoAnnoPrecedente": 2, "maturato": 2.16,
                         "goduto": null, "saldo": 4.16}],
              "tfr": null,
              "presenze": {"giorniLavorati": 21, "oreLavorate": 168, "giorniRetribuiti": null},
              "scatti": {"numeroScatti": 1, "dataProssimoScatto": "2026-02-01"},
              "buoniPasto": {"presenti": true, "numero": 20, "valoreUnitario": 8.00, "importo": 160.00},
              "progressivi": {"imponibilePrevidenziale": 12000.00, "imponibileIrpef": null, "irpefTrattenuta": null},
              "contieneTredicesima": false,
              "contieneQuattordicesima": false
            }
            """;

    @BeforeEach
    void anagraficaDelRapporto() {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(anagrafica.elencoPersone())
                .thenReturn(List.of(DatiDiProva.personaSintesi("Mario Rossi (" + DatiDiProva.CF_ROSSI + ")")));
    }

    // ------------------------------------------------------------------------------------------ elenco e PDF

    @Test
    void elencoConLaMensilitaAssociataComposta() throws Exception {
        when(caricamentiInErrore.elenco()).thenReturn(List.of(
                fileInErrore(41, "maggio.pdf", MotivoErrore.PDF_PROTETTO, MAGGIO, false),
                fileInErrore(42, "tredicesima.pdf", MotivoErrore.PDF_SENZA_TESTO,
                        Competenza.Mensilita.tredicesima(2023), true),
                fileInErrore(40, "vecchio.pdf", null, null, false)));

        mvc.perform(get("/api/v1/caricamenti/in-errore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(41))
                .andExpect(jsonPath("$[0].lotto").value(LOTTO_IN_ERRORE.toString()))
                .andExpect(jsonPath("$[0].nomeFileOriginale").value("maggio.pdf"))
                .andExpect(jsonPath("$[0].dimensione").value(2048))
                .andExpect(jsonPath("$[0].motivo").value("PDF_PROTETTO"))
                .andExpect(jsonPath("$[0].messaggio").value("Il lettore non riesce a leggere questo file"))
                .andExpect(jsonPath("$[0].pdfDisponibile").value(true))
                .andExpect(jsonPath("$[0].inElaborazione").value(false))
                .andExpect(jsonPath("$[0].creatoIl").value("2025-03-10T09:30:00Z"))
                .andExpect(jsonPath("$[0].attribuzione.rapportoId").value(7))
                .andExpect(jsonPath("$[0].attribuzione.personaId").value(1))
                .andExpect(jsonPath("$[0].attribuzione.persona").value("Mario Rossi (" + DatiDiProva.CF_ROSSI + ")"))
                .andExpect(jsonPath("$[0].attribuzione.azienda").value("Alfa Software S.r.l."))
                .andExpect(jsonPath("$[0].attribuzione.competenza.chiave").value("2023.05"))
                .andExpect(jsonPath("$[0].attribuzione.competenza.tipo").value("ORDINARIA"))
                .andExpect(jsonPath("$[0].attribuzione.titolo").value("Maggio 2023"))
                .andExpect(jsonPath("$[0].attribuzione.competenzaPresente").value(false))
                .andExpect(jsonPath("$[1].attribuzione.titolo").value("Tredicesima 2023"))
                .andExpect(jsonPath("$[1].attribuzione.competenza.mese").value(13))
                .andExpect(jsonPath("$[1].attribuzione.competenzaPresente").value(true))
                .andExpect(jsonPath("$[2].motivo").value(nullValue()))
                .andExpect(jsonPath("$[2].pdfDisponibile").value(false))
                .andExpect(jsonPath("$[2]").value(hasKey("attribuzione")))
                .andExpect(jsonPath("$[2].attribuzione").value(nullValue()));
        // un rapporto letto una volta sola per composizione
        verify(anagrafica, times(1)).dettaglioRapporto(RAPPORTO);
    }

    @Test
    void elencoVuoto() throws Exception {
        when(caricamentiInErrore.elenco()).thenReturn(List.of());

        mvc.perform(get("/api/v1/caricamenti/in-errore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void unRapportoNonPiuEsistenteLasciaIlFileSenzaAssociazione() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO)).thenReturn(Optional.empty());
        when(caricamentiInErrore.elenco())
                .thenReturn(List.of(fileInErrore(41, "maggio.pdf", MotivoErrore.PDF_PROTETTO, MAGGIO, false)));

        mvc.perform(get("/api/v1/caricamenti/in-errore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(41))
                .andExpect(jsonPath("$[0].attribuzione").value(nullValue()));
    }

    @Test
    void pdfConservatoConIlNomeOriginale() throws Exception {
        when(caricamentiInErrore.pdf(CARICAMENTO)).thenReturn(Optional.of(new PdfCedolino("cedolino maggio.pdf", PDF)));

        mvc.perform(get("/api/v1/caricamenti/40/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"cedolino maggio.pdf\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''cedolino%20maggio.pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void pdfNonConservato404AncheChiedendoUnPdfEIdNonValido400() throws Exception {
        when(caricamentiInErrore.pdf(new CaricamentoId(99))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/caricamenti/99/pdf").accept(MediaType.APPLICATION_PDF, MediaType.ALL))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("PDF conservato del caricamento inesistente (id 99)."));
        mvc.perform(get("/api/v1/caricamenti/0/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Messaggi.ID_NON_VALIDO));
    }

    // ------------------------------------------------------------------------------------------ rimozione

    @Test
    void rimozione204() throws Exception {
        mvc.perform(delete("/api/v1/caricamenti/40"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(caricamentiInErrore).rimuovi(CARICAMENTO);
    }

    @Test
    void rimozioneInesistente404NonInErrore409IdNonValido400() throws Exception {
        doThrow(new RisorsaNonTrovataException("Caricamento", 99L)).when(caricamentiInErrore)
                .rimuovi(new CaricamentoId(99));
        doThrow(new OperazioneNonConsentitaException(NON_IN_ERRORE)).when(caricamentiInErrore).rimuovi(CARICAMENTO);

        mvc.perform(delete("/api/v1/caricamenti/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Caricamento inesistente (id 99)."));
        mvc.perform(delete("/api/v1/caricamenti/40"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Operazione non consentita"))
                .andExpect(jsonPath("$.detail").value(NON_IN_ERRORE))
                .andExpect(jsonPath("$.errori").doesNotExist());
        mvc.perform(delete("/api/v1/caricamenti/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Messaggi.ID_NON_VALIDO));
        mvc.perform(delete("/api/v1/caricamenti/in-errore"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------------ nuova lettura

    @Test
    void riletturaInUnNuovoLottoConLaPasswordMaiRestituitaNeRegistrata(CapturedOutput output) throws Exception {
        when(caricamentiInErrore.rileggi(CARICAMENTO, PASSWORD)).thenReturn(List.of(inCoda(50)));
        var logWeb = (Logger) LoggerFactory.getLogger("org.springframework.web");
        var livello = logWeb.getLevel();
        logWeb.setLevel(Level.TRACE);
        try {
            mvc.perform(post("/api/v1/caricamenti/40/rilettura")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\": \"" + PASSWORD + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/caricamenti/lotti/" + NUOVO_LOTTO)))
                    .andExpect(jsonPath("$.lotto").value(NUOVO_LOTTO.toString()))
                    .andExpect(jsonPath("$.completato").value(false))
                    .andExpect(jsonPath("$.caricamenti[0].id").value(50))
                    .andExpect(jsonPath("$.caricamenti[0].stato").value("IN_CODA"))
                    .andExpect(jsonPath("$.caricamenti[0].nomeFileOriginale").value("maggio.pdf"))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        } finally {
            logWeb.setLevel(livello);
        }

        verify(caricamentiInErrore).rileggi(CARICAMENTO, PASSWORD);
        assertThat(output.getAll()).contains("RichiestaRiletturaDto[password=***]").doesNotContain(PASSWORD);
        assertThat(new RichiestaRiletturaDto(PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new RichiestaRiletturaDto(null).toString()).isEqualTo("RichiestaRiletturaDto[password=assente]");
    }

    @Test
    void riletturaSenzaPasswordOConPasswordVuota() throws Exception {
        when(caricamentiInErrore.rileggi(eq(CARICAMENTO), isNull())).thenReturn(List.of(inCoda(50)));

        mvc.perform(post("/api/v1/caricamenti/40/rilettura").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": null}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/caricamenti/40/rilettura").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"\"}"))
                .andExpect(status().isAccepted());
        verify(caricamentiInErrore, times(2)).rileggi(CARICAMENTO, null);
    }

    @Test
    void passwordTroppoLunga400SenzaRiportarla(CapturedOutput output) throws Exception {
        var lunga = "p".repeat(150) + PASSWORD + "q".repeat(50);

        mvc.perform(post("/api/v1/caricamenti/40/rilettura").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + lunga + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La password dei PDF può essere lunga al massimo 200 caratteri."))
                .andExpect(content().string(not(containsString(PASSWORD))));
        verify(caricamentiInErrore, never()).rileggi(any(), any());
        assertThat(output.getAll()).doesNotContain(PASSWORD);
        assertThat(RichiestaRiletturaDto.LUNGHEZZA_MASSIMA_PASSWORD)
                .isEqualTo(ValidazioneCaricamento.LUNGHEZZA_MASSIMA_PASSWORD);
    }

    @Test
    void riletturaSenzaPdfConservato409ESenzaCorpo400() throws Exception {
        when(caricamentiInErrore.rileggi(CARICAMENTO, null))
                .thenThrow(new OperazioneNonConsentitaException("PDF del file non conservato: caricalo di nuovo"));

        mvc.perform(post("/api/v1/caricamenti/40/rilettura").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("PDF del file non conservato: caricalo di nuovo"));
        mvc.perform(post("/api/v1/caricamenti/40/rilettura").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo della richiesta mancante o non valido (JSON atteso)."));
    }

    // ------------------------------------------------------------------------------------------ associazione

    @Test
    void associaAUnaMensilita() throws Exception {
        when(caricamentiInErrore.associa(CARICAMENTO, RAPPORTO, 2023, 5))
                .thenReturn(fileInErrore(40, "maggio.pdf", MotivoErrore.PDF_PROTETTO, MAGGIO, false));

        mvc.perform(put("/api/v1/caricamenti/40/attribuzione").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rapportoId\": 7, \"anno\": 2023, \"mese\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.attribuzione.rapportoId").value(7))
                .andExpect(jsonPath("$.attribuzione.persona").value("Mario Rossi (" + DatiDiProva.CF_ROSSI + ")"))
                .andExpect(jsonPath("$.attribuzione.azienda").value("Alfa Software S.r.l."))
                .andExpect(jsonPath("$.attribuzione.titolo").value("Maggio 2023"));
    }

    @Test
    void associazioneConDatiNonValidi400ConGliErroriPerCampo() throws Exception {
        when(caricamentiInErrore.associa(eq(CARICAMENTO), isNull(), isNull(), eq(15)))
                .thenThrow(new DatiNonValidiException(List.of(
                        new ErroreCampo("rapportoId", "Indica il rapporto di lavoro"),
                        new ErroreCampo("anno", "Indica l'anno (dal 1900 al 2027)"),
                        new ErroreCampo("mese", "Indica il mese (da 1 a 12, 13 per la tredicesima, 14 per la "
                                + "quattordicesima)"))));

        mvc.perform(put("/api/v1/caricamenti/40/attribuzione").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rapportoId\": 0, \"anno\": null, \"mese\": 15}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://salary-manager.local/problemi/richiesta-non-valida"))
                .andExpect(jsonPath("$.title").value("Richiesta non valida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("3 campi da correggere"))
                .andExpect(jsonPath("$.errori[*].campo").value(contains("rapportoId", "anno", "mese")))
                .andExpect(jsonPath("$.errori[0].messaggio").value("Indica il rapporto di lavoro"));
    }

    @Test
    void associazioneAUnaMensilitaGiaPresente409EAUnRapportoInesistente404() throws Exception {
        when(caricamentiInErrore.associa(CARICAMENTO, RAPPORTO, 2023, 5)).thenThrow(
                new OperazioneNonConsentitaException("Il cedolino di Maggio 2023 è già presente nel rapporto"));
        when(caricamentiInErrore.associa(CARICAMENTO, new RapportoLavoroId(99), 2023, 5))
                .thenThrow(new RisorsaNonTrovataException("Rapporto di lavoro", 99L));

        mvc.perform(put("/api/v1/caricamenti/40/attribuzione").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rapportoId\": 7, \"anno\": 2023, \"mese\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Il cedolino di Maggio 2023 è già presente nel rapporto"));
        mvc.perform(put("/api/v1/caricamenti/40/attribuzione").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rapportoId\": 99, \"anno\": 2023, \"mese\": 5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Rapporto di lavoro inesistente (id 99)."));
    }

    @Test
    void rimuoviAssociazione() throws Exception {
        when(caricamentiInErrore.rimuoviAssociazione(CARICAMENTO))
                .thenReturn(fileInErrore(40, "maggio.pdf", MotivoErrore.PDF_PROTETTO, null, false));

        mvc.perform(delete("/api/v1/caricamenti/40/attribuzione"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.attribuzione").value(nullValue()));
    }

    @Test
    void rimuoviAssociazioneDiUnFileInElaborazione409() throws Exception {
        when(caricamentiInErrore.rimuoviAssociazione(CARICAMENTO))
                .thenThrow(new OperazioneNonConsentitaException("Il file è in elaborazione: attendi la fine"));

        mvc.perform(delete("/api/v1/caricamenti/40/attribuzione"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Il file è in elaborazione: attendi la fine"));
    }

    // ------------------------------------------------------------------------------------------ inserimento a mano

    @Test
    void verificaConIlCedolinoConvertitoCampoPerCampo() throws Exception {
        when(caricamentiInErrore.verifica(eq(CARICAMENTO), any())).thenReturn(new VerificaCedolinoManuale(
                Esito.ACQUISIBILE, null, MAGGIO, false, true, true, null, null,
                List.of("Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale")));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content(CEDOLINO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("ACQUISIBILE"))
                .andExpect(jsonPath("$.messaggio").value(nullValue()))
                .andExpect(jsonPath("$.competenza.chiave").value("2023.05"))
                .andExpect(jsonPath("$.titolo").value("Maggio 2023"))
                .andExpect(jsonPath("$.nuovaPersona").value(false))
                .andExpect(jsonPath("$.nuovaAzienda").value(true))
                .andExpect(jsonPath("$.nuovoRapporto").value(true))
                .andExpect(jsonPath("$.rapportoId").value(nullValue()))
                .andExpect(jsonPath("$.cedolinoEsistenteId").value(nullValue()))
                .andExpect(jsonPath("$.avvisi", hasSize(1)));

        var dati = ArgumentCaptor.forClass(CedolinoManuale.class);
        verify(caricamentiInErrore).verifica(eq(CARICAMENTO), dati.capture());
        var c = dati.getValue();
        assertThat(c.personaId()).as("persona da creare: nessuna scelta").isNull();
        assertThat(c.aziendaId()).isNull();
        assertThat(c.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(DatiDiProva.CF_ROSSI, "Mario", "Rossi",
                LocalDate.of(1980, 1, 1), "D001"));
        assertThat(c.datoreLavoro()).isEqualTo(new CedolinoManuale.DatoreLavoro(null, "IT01234567897", "1122334455",
                null, "Alfa Software S.r.l.", "Via Roma 1, Milano", "A01"));
        assertThat(c.datiContrattuali()).isEqualTo(new CedolinoManuale.DatiContrattuali(LocalDate.of(2023, 1, 9),
                null, "Impiegato", "3", "CCNL Commercio", "H011"));
        assertThat(c.periodo()).isEqualTo(new CedolinoManuale.Periodo(2023, 5, TipoMensilita.ORDINARIA,
                LocalDate.of(2023, 5, 31), YearMonth.of(2023, 4)));
        assertThat(c.totali().netto()).isEqualByComparingTo("1810");
        assertThat(c.totali().arrotondamento()).isEqualByComparingTo("-0.42");
        assertThat(c.retribuzione().superminimoAssorbibile()).isTrue();
        assertThat(c.retribuzione().pagaGiornaliera()).isEqualByComparingTo("90.38462");
        assertThat(c.retribuzione().divisoreGiornaliero()).isEqualByComparingTo("26");
        assertThat(c.voci()).hasSize(2);
        assertThat(c.voci().getFirst().categoria()).isEqualTo(CategoriaVoce.COMPETENZA);
        assertThat(c.voci().getFirst().quantita()).isEqualByComparingTo("168");
        assertThat(c.voci().get(1).importoTrattenuta()).isEqualByComparingTo("369.26");
        assertThat(c.ratei()).singleElement().satisfies(r -> {
            assertThat(r.tipo()).isEqualTo(TipoRateo.FERIE);
            assertThat(r.unita()).isEqualTo(UnitaMisura.GIORNI);
            assertThat(r.saldo()).isEqualByComparingTo("4.16");
            assertThat(r.goduto()).isNull();
        });
        assertThat(c.tfr()).isNull();
        assertThat(c.presenze().oreLavorate()).isEqualByComparingTo("168");
        assertThat(c.scatti()).isEqualTo(new CedolinoManuale.Scatti(1, LocalDate.of(2026, 2, 1)));
        assertThat(c.buoniPasto().presenti()).isTrue();
        assertThat(c.buoniPasto().importo()).isEqualByComparingTo(new BigDecimal("160"));
        assertThat(c.progressivi().imponibilePrevidenziale()).isEqualByComparingTo("12000");
        assertThat(c.contieneTredicesima()).isFalse();
    }

    @Test
    void leAnagraficheScelteDallElencoArrivanoAlServizio() throws Exception {
        when(caricamentiInErrore.verifica(eq(CARICAMENTO), any())).thenReturn(new VerificaCedolinoManuale(
                Esito.ACQUISIBILE, null, MAGGIO, false, false, false, RAPPORTO, null, List.of()));
        when(caricamentiInErrore.inserisci(eq(CARICAMENTO), any())).thenReturn(new Caricamento(CARICAMENTO,
                LOTTO_IN_ERRORE, "maggio.pdf", 2048, StatoCaricamento.ACQUISITO, null, null, false, new CedolinoId(60),
                null, new PersonaId(1), RAPPORTO, ISTANTE, ISTANTE.plusSeconds(30)));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content(conScelte(1, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuovaPersona").value(false))
                .andExpect(jsonPath("$.nuovaAzienda").value(false))
                .andExpect(jsonPath("$.nuovoRapporto").value(false))
                .andExpect(jsonPath("$.rapportoId").value(7));
        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON)
                        .content(conScelte(1, 3)))
                .andExpect(status().isCreated());

        var verificato = ArgumentCaptor.forClass(CedolinoManuale.class);
        verify(caricamentiInErrore).verifica(eq(CARICAMENTO), verificato.capture());
        assertThat(verificato.getValue().personaId()).isEqualTo(1L);
        assertThat(verificato.getValue().aziendaId()).isEqualTo(3L);
        var inserito = ArgumentCaptor.forClass(CedolinoManuale.class);
        verify(caricamentiInErrore).inserisci(eq(CARICAMENTO), inserito.capture());
        assertThat(inserito.getValue().personaId()).isEqualTo(1L);
        assertThat(inserito.getValue().aziendaId()).isEqualTo(3L);
        assertThat(inserito.getValue().dipendente().codiceFiscale()).isEqualTo(DatiDiProva.CF_ROSSI);
    }

    @Test
    void unaSceltaNonPiuInArchivio400SulCampoDellaScelta() throws Exception {
        when(caricamentiInErrore.inserisci(eq(CARICAMENTO), any())).thenThrow(new DatiNonValidiException(List.of(
                new ErroreCampo("personaId", "La persona scelta non è più in archivio: scegline un'altra o creane "
                        + "una nuova"))));
        when(caricamentiInErrore.verifica(eq(CARICAMENTO), any())).thenThrow(new DatiNonValidiException(List.of(
                new ErroreCampo("datoreLavoro.partitaIva", "Questa partita IVA è già in archivio: seleziona "
                        + "l'azienda dall'elenco"))));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON)
                        .content(conScelte(987654, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("La persona scelta non è più in archivio: scegline un'altra o creane una nuova"))
                .andExpect(jsonPath("$.errori", hasSize(1)))
                .andExpect(jsonPath("$.errori[0].campo").value("personaId"));
        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content(CEDOLINO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errori[0].campo").value("datoreLavoro.partitaIva"))
                .andExpect(content().string(not(containsString("01234567897"))));
    }

    @Test
    void unIdentificativoDiSceltaNonNumericoEUnCorpoNonValido() throws Exception {
        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personaId\": \"Mario Rossi\", " + CEDOLINO.strip().substring(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo della richiesta mancante o non valido (JSON atteso)."));
        verify(caricamentiInErrore, never()).verifica(any(), any());
    }

    @Test
    void verificaDiUnDuplicato() throws Exception {
        when(caricamentiInErrore.verifica(eq(CARICAMENTO), any())).thenReturn(new VerificaCedolinoManuale(
                Esito.DUPLICATO, "Cedolino di Maggio 2023 già presente con gli stessi importi: scartato", null,
                false, false, false, RAPPORTO, new CedolinoId(16), List.of()));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content(CEDOLINO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DUPLICATO"))
                .andExpect(jsonPath("$.messaggio")
                        .value("Cedolino di Maggio 2023 già presente con gli stessi importi: scartato"))
                .andExpect(jsonPath("$.competenza").value(nullValue()))
                .andExpect(jsonPath("$.titolo").value(nullValue()))
                .andExpect(jsonPath("$.rapportoId").value(7))
                .andExpect(jsonPath("$.cedolinoEsistenteId").value(16))
                .andExpect(jsonPath("$.avvisi", empty()));
    }

    @Test
    void areeEListeAssentiNonSonoUnErroreDelServer() throws Exception {
        when(caricamentiInErrore.verifica(eq(CARICAMENTO), any())).thenThrow(new DatiNonValidiException(
                "periodo.anno", "Indica l'anno (dal 1900 al 2027)"));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voci": [null], "ratei": null, "contieneTredicesima": false,
                                 "contieneQuattordicesima": false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Indica l'anno (dal 1900 al 2027)"))
                .andExpect(jsonPath("$.errori", hasSize(1)))
                .andExpect(jsonPath("$.errori[0].campo").value("periodo.anno"));

        var dati = ArgumentCaptor.forClass(CedolinoManuale.class);
        verify(caricamentiInErrore).verifica(eq(CARICAMENTO), dati.capture());
        var c = dati.getValue();
        assertThat(c.dipendente()).isNull();
        assertThat(c.periodo()).isNull();
        assertThat(c.totali()).isNull();
        assertThat(c.tfr()).isNull();
        assertThat(c.voci()).as("l'elemento nullo resta: lo segnala la validazione sul suo percorso").hasSize(1)
                .containsOnlyNulls();
        assertThat(c.ratei()).isEmpty();
        assertThat(c.contieneTredicesima()).isFalse();
    }

    @Test
    void inserimento201ConLaPosizioneDelCedolino() throws Exception {
        when(caricamentiInErrore.inserisci(eq(CARICAMENTO), any())).thenReturn(new Caricamento(CARICAMENTO,
                LOTTO_IN_ERRORE, "maggio.pdf", 2048, StatoCaricamento.ACQUISITO, null, null, false, new CedolinoId(60),
                null, new PersonaId(1), RAPPORTO, ISTANTE, ISTANTE.plusSeconds(30)));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON).content(CEDOLINO))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/cedolini/60")))
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.stato").value("ACQUISITO"))
                .andExpect(jsonPath("$.motivo").value(nullValue()))
                .andExpect(jsonPath("$.pdfDisponibile").value(false))
                .andExpect(jsonPath("$.cedolinoId").value(60))
                .andExpect(jsonPath("$.personaId").value(1))
                .andExpect(jsonPath("$.rapportoId").value(7));
        verify(caricamentiInErrore).inserisci(eq(CARICAMENTO), any(CedolinoManuale.class));
    }

    @Test
    void inserimentoConDatiNonValidi400ConIPercorsiDelloSchema() throws Exception {
        when(caricamentiInErrore.inserisci(eq(CARICAMENTO), any())).thenThrow(new DatiNonValidiException(List.of(
                new ErroreCampo("totali.netto", "Indica il netto"),
                new ErroreCampo("voci[1].descrizione", "Indica la descrizione della voce"),
                new ErroreCampo(null, "Dati del cedolino non validi"))));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON).content(CEDOLINO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("3 campi da correggere"))
                .andExpect(jsonPath("$.errori[0].campo").value("totali.netto"))
                .andExpect(jsonPath("$.errori[0].messaggio").value("Indica il netto"))
                .andExpect(jsonPath("$.errori[1].campo").value("voci[1].descrizione"))
                .andExpect(jsonPath("$.errori[2]").value(hasKey("campo")))
                .andExpect(jsonPath("$.errori[2].campo").value(nullValue()))
                .andExpect(content().string(not(containsString("RSSMRA80A01H501U"))));
    }

    @Test
    void inserimentoDiUnDuplicato409EDiUnCaricamentoInesistente404() throws Exception {
        when(caricamentiInErrore.inserisci(eq(CARICAMENTO), any())).thenThrow(new OperazioneNonConsentitaException(
                "Cedolino di Maggio 2023 già presente con gli stessi importi: scartato"));
        when(caricamentiInErrore.inserisci(eq(new CaricamentoId(99)), any()))
                .thenThrow(new RisorsaNonTrovataException("Caricamento", 99L));

        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON).content(CEDOLINO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Cedolino di Maggio 2023 già presente con gli stessi importi: scartato"));
        mvc.perform(post("/api/v1/caricamenti/99/cedolino").contentType(MediaType.APPLICATION_JSON).content(CEDOLINO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Caricamento inesistente (id 99)."));
    }

    @Test
    void corpoNonValido400SenzaErroriPerCampo() throws Exception {
        mvc.perform(post("/api/v1/caricamenti/40/cedolino").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totali\": {\"netto\": \"milleottocento\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo della richiesta mancante o non valido (JSON atteso)."))
                .andExpect(jsonPath("$.errori").doesNotExist());
        mvc.perform(post("/api/v1/caricamenti/40/cedolino/verifica").contentType(MediaType.TEXT_PLAIN)
                        .content("cedolino"))
                .andExpect(status().isUnsupportedMediaType());
        verify(caricamentiInErrore, never()).inserisci(any(), any());
        verify(caricamentiInErrore, never()).verifica(any(), any());
        verify(caricamentiInErrore, never()).associa(any(), any(), any(), any());
        verify(anagrafica, never()).trovaRapporto(any());
        verify(caricamento, never()).avvia(any(), anyString());
    }

    // ------------------------------------------------------------------------------------------ supporto

    /** Il cedolino di prova con la persona e l'azienda scelte dall'elenco. */
    private static String conScelte(long personaId, long aziendaId) {
        return "{\"personaId\": " + personaId + ", \"aziendaId\": " + aziendaId + ", "
                + CEDOLINO.strip().substring(1);
    }

    private static Caricamento inCoda(long id) {
        return new Caricamento(new CaricamentoId(id), NUOVO_LOTTO, "maggio.pdf", PDF.length, StatoCaricamento.IN_CODA,
                null, null, false, null, null, null, null, ISTANTE, ISTANTE);
    }
}
