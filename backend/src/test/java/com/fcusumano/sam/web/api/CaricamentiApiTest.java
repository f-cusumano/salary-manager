package com.fcusumano.sam.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;

@ExtendWith(OutputCaptureExtension.class)
class CaricamentiApiTest extends ApiWebMvcTest {

    private static final UUID LOTTO = UUID.fromString("4b5f6a7e-1c2d-4e3f-8a9b-0c1d2e3f4a5b");
    private static final String PASSWORD = "Segreto-Lotto-42";
    private static final byte[] PDF = "%PDF-1.4\n% prova\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void caricaSenzaPassword() throws Exception {
        when(caricamento.avvia(anyList(), isNull())).thenReturn(List.of(
                inCoda(1, "gennaio.pdf"), inCoda(2, "febbraio.pdf")));

        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("gennaio.pdf"))
                        .file(new MockMultipartFile("files", "febbraio.pdf", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                PDF)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/caricamenti/lotti/" + LOTTO)))
                .andExpect(jsonPath("$.lotto").value(LOTTO.toString()))
                .andExpect(jsonPath("$.completato").value(false))
                .andExpect(jsonPath("$.caricamenti[0].nomeFileOriginale").value("gennaio.pdf"))
                .andExpect(jsonPath("$.caricamenti[0].stato").value("IN_CODA"))
                .andExpect(jsonPath("$.caricamenti[0].dimensione").value(PDF.length))
                .andExpect(jsonPath("$.caricamenti[0].personaId").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[0].rapportoId").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[1].id").value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileCaricato>> files = ArgumentCaptor.forClass(List.class);
        verify(caricamento).avvia(files.capture(), isNull());
        assertThat(files.getValue()).extracting(FileCaricato::nomeFileOriginale)
                .containsExactly("gennaio.pdf", "febbraio.pdf");
        assertThat(files.getValue().getFirst().contenuto()).isEqualTo(PDF);
    }

    @Test
    void caricaConPasswordSenzaMaiRestituirlaORegistrarla(CapturedOutput output) throws Exception {
        when(caricamento.avvia(anyList(), eq(PASSWORD))).thenReturn(List.of(inCoda(1, "protetto.pdf")));

        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("protetto.pdf"))
                        .part(new MockPart("password", PASSWORD.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted())
                .andExpect(content().string(not(containsString(PASSWORD))));

        verify(caricamento).avvia(anyList(), eq(PASSWORD));
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void passwordVuotaEquivaleAdAssente() throws Exception {
        when(caricamento.avvia(anyList(), isNull())).thenReturn(List.of(inCoda(1, "gennaio.pdf")));

        mvc.perform(multipart("/api/v1/caricamenti").file(pdf("gennaio.pdf")).part(new MockPart("password", new byte[0])))
                .andExpect(status().isAccepted());
        verify(caricamento).avvia(anyList(), isNull());
    }

    @Test
    void passwordTroppoLunga400SenzaRiportarla(CapturedOutput output) throws Exception {
        var lunga = "p".repeat(150) + PASSWORD + "q".repeat(50);

        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("gennaio.pdf"))
                        .part(new MockPart("password", lunga.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La password dei PDF può essere lunga al massimo 200 caratteri."))
                .andExpect(content().string(not(containsString(PASSWORD))));
        verify(caricamento, never()).avvia(anyList(), any());
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void senzaFile400() throws Exception {
        mvc.perform(multipart("/api/v1/caricamenti").part(new MockPart("password", "x".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Parte obbligatoria mancante nella richiesta: \"files\"."));
        mvc.perform(multipart("/api/v1/caricamenti").file(new MockMultipartFile("files", "", null, new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Seleziona almeno un PDF da caricare (campo \"files\")."));
        verify(caricamento, never()).avvia(anyList(), any());
    }

    @Test
    void richiestaNonMultipart400() throws Exception {
        mvc.perform(post("/api/v1/caricamenti")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo di contenuto non supportato"));
    }

    @Test
    void fileVuoto400() throws Exception {
        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("gennaio.pdf"))
                        .file(new MockMultipartFile("files", "vuoto.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Il file \"vuoto.pdf\" è vuoto."));
        verify(caricamento, never()).avvia(anyList(), any());
    }

    @Test
    void soloPdfPerEstensioneOTipoEIntestazione() throws Exception {
        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("gennaio.pdf"))
                        .file(new MockMultipartFile("files", "note.txt", MediaType.TEXT_PLAIN_VALUE,
                                "testo".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "C:\\Documenti\\finto.pdf", MediaType.APPLICATION_PDF_VALUE,
                                "<html>non sono un pdf</html>".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "foto.png", "image/png", PDF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Sono ammessi solo file PDF: \"note.txt\", \"finto.pdf\", \"foto.png\" non sono PDF validi."));
        verify(caricamento, never()).avvia(anyList(), any());
    }

    @Test
    void pdfSenzaEstensioneMaConTipoPdfAccettato() throws Exception {
        when(caricamento.avvia(anyList(), isNull())).thenReturn(List.of(inCoda(1, "cedolino")));
        var conPreambolo = new byte[PDF.length + 3];
        conPreambolo[0] = (byte) 0xEF;
        conPreambolo[1] = (byte) 0xBB;
        conPreambolo[2] = (byte) 0xBF;
        System.arraycopy(PDF, 0, conPreambolo, 3, PDF.length);

        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(new MockMultipartFile("files", "cedolino", MediaType.APPLICATION_PDF_VALUE, conPreambolo)))
                .andExpect(status().isAccepted());
    }

    @Test
    void fileOltreIlLimite413() throws Exception {
        var grande = new byte[25 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        mvc.perform(multipart("/api/v1/caricamenti")
                        .file(pdf("gennaio.pdf"))
                        .file(new MockMultipartFile("files", "enorme.pdf", MediaType.APPLICATION_PDF_VALUE, grande)))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("File troppo grande"))
                .andExpect(jsonPath("$.detail").value("Il file \"enorme.pdf\" supera la dimensione massima di 25 MB per file."));
        verify(caricamento, never()).avvia(anyList(), any());
    }

    @Test
    void caricamentiRecenti() throws Exception {
        var acquisito = new Caricamento(new CaricamentoId(3), LOTTO, "marzo.pdf", 1234, StatoCaricamento.ACQUISITO,
                null, null, false, new CedolinoId(30), null, new PersonaId(1), new RapportoLavoroId(7), DatiDiProva.ISTANTE,
                DatiDiProva.ISTANTE.plusSeconds(20));
        var duplicato = new Caricamento(new CaricamentoId(2), LOTTO, "marzo-copia.pdf", 1234,
                StatoCaricamento.DUPLICATO, "Lo stesso file è già stato caricato: scartato", null, false, null,
                new CedolinoId(30),
                new PersonaId(1), new RapportoLavoroId(7), DatiDiProva.ISTANTE, DatiDiProva.ISTANTE.plusSeconds(5));
        when(caricamento.recenti(50)).thenReturn(List.of(acquisito, duplicato));
        when(caricamento.recenti(5)).thenReturn(List.of(acquisito));

        mvc.perform(get("/api/v1/caricamenti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stato").value("ACQUISITO"))
                .andExpect(jsonPath("$[0].cedolinoId").value(30))
                .andExpect(jsonPath("$[0].cedolinoEsistenteId").value(nullValue()))
                .andExpect(jsonPath("$[0].personaId").value(1))
                .andExpect(jsonPath("$[0].rapportoId").value(7))
                .andExpect(jsonPath("$[0].creatoIl").value("2025-03-10T09:30:00Z"))
                .andExpect(jsonPath("$[0].aggiornatoIl").value("2025-03-10T09:30:20Z"))
                .andExpect(jsonPath("$[1].stato").value("DUPLICATO"))
                .andExpect(jsonPath("$[1].messaggio").value("Lo stesso file è già stato caricato: scartato"))
                .andExpect(jsonPath("$[1].cedolinoEsistenteId").value(30))
                .andExpect(jsonPath("$[1].personaId").value(1))
                .andExpect(jsonPath("$[1].rapportoId").value(7));
        mvc.perform(get("/api/v1/caricamenti").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void limiteFuoriIntervallo400() throws Exception {
        for (var limite : List.of("0", "201", "tanti")) {
            mvc.perform(get("/api/v1/caricamenti").param("limite", limite))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/v1/caricamenti").param("limite", "201"))
                .andExpect(jsonPath("$.detail").value(Messaggi.LIMITE_NON_VALIDO));
        verify(caricamento, never()).recenti(anyInt());
    }

    @Test
    void statoDelLotto() throws Exception {
        var inLettura = new Caricamento(new CaricamentoId(1), LOTTO, "gennaio.pdf", 100, StatoCaricamento.IN_LETTURA,
                null, null, false, null, null, null, null, DatiDiProva.ISTANTE, DatiDiProva.ISTANTE);
        var aggiuntivo = new Caricamento(new CaricamentoId(2), LOTTO, "premio.pdf", 100,
                StatoCaricamento.AGGIUNTIVO_NON_GESTITO, "Cedolino aggiuntivo di Marzo 2024: non gestito", null, false,
                null, new CedolinoId(18), new PersonaId(2), new RapportoLavoroId(9), DatiDiProva.ISTANTE, DatiDiProva.ISTANTE);
        var errore = new Caricamento(new CaricamentoId(3), LOTTO, "protetto.pdf", 100, StatoCaricamento.ERRORE,
                "PDF protetto da password: riprova indicando la password", MotivoErrore.PDF_PROTETTO, true, null, null,
                null, null, DatiDiProva.ISTANTE, DatiDiProva.ISTANTE);
        var acquisito = new Caricamento(new CaricamentoId(1), LOTTO, "gennaio.pdf", 100, StatoCaricamento.ACQUISITO,
                null, null, false, new CedolinoId(16), null, new PersonaId(2), new RapportoLavoroId(9), DatiDiProva.ISTANTE,
                DatiDiProva.ISTANTE);
        when(caricamento.statoLotto(LOTTO))
                .thenReturn(List.of(inLettura, aggiuntivo, errore))
                .thenReturn(List.of(acquisito, aggiuntivo, errore));

        mvc.perform(get("/api/v1/caricamenti/lotti/" + LOTTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotto").value(LOTTO.toString()))
                .andExpect(jsonPath("$.completato").value(false))
                .andExpect(jsonPath("$.caricamenti[1].stato").value("AGGIUNTIVO_NON_GESTITO"))
                .andExpect(jsonPath("$.caricamenti[1].cedolinoEsistenteId").value(18))
                .andExpect(jsonPath("$.caricamenti[1].personaId").value(2))
                .andExpect(jsonPath("$.caricamenti[2].personaId").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[2].rapportoId").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[2].messaggio")
                        .value("PDF protetto da password: riprova indicando la password"))
                .andExpect(jsonPath("$.caricamenti[2].motivo").value("PDF_PROTETTO"))
                .andExpect(jsonPath("$.caricamenti[2].pdfDisponibile").value(true))
                .andExpect(jsonPath("$.caricamenti[0].motivo").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[0].pdfDisponibile").value(false))
                .andExpect(jsonPath("$.caricamenti[1].motivo").value(nullValue()))
                .andExpect(jsonPath("$.caricamenti[1].pdfDisponibile").value(false));
        mvc.perform(get("/api/v1/caricamenti/lotti/" + LOTTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completato").value(true))
                .andExpect(jsonPath("$.caricamenti[0].cedolinoId").value(16));
    }

    @Test
    void lottoInesistente404ONonValido400() throws Exception {
        var sconosciuto = UUID.fromString("00000000-0000-4000-8000-000000000001");
        when(caricamento.statoLotto(sconosciuto)).thenReturn(List.of());

        mvc.perform(get("/api/v1/caricamenti/lotti/" + sconosciuto))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Lotto di caricamento inesistente (id " + sconosciuto + ")."));
        mvc.perform(get("/api/v1/caricamenti/lotti/non-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valore non valido per \"lotto\"."));
    }

    private static MockMultipartFile pdf(String nome) {
        return new MockMultipartFile("files", nome, MediaType.APPLICATION_PDF_VALUE, PDF);
    }

    private static Caricamento inCoda(long id, String nome) {
        return new Caricamento(new CaricamentoId(id), LOTTO, nome, PDF.length, StatoCaricamento.IN_CODA, null, null,
                false, null, null, null, null, DatiDiProva.ISTANTE, DatiDiProva.ISTANTE);
    }
}
