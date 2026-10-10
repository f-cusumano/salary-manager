package com.fcusumano.sam.cedolini.adapter.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Lettore a fixture: associazione PDF -> JSON (nome file, hash, marcatore nel testo) e coerenza del set di fixture
 * inventate (tutte valide rispetto allo schema e ai controlli semantici).
 */
class FixtureLettoreCedoliniTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private final FixtureLettoreCedolini lettore = new FixtureLettoreCedolini("classpath:fixtures/cedolini/",
            new PathMatchingResourcePatternResolver(), InterpreteRispostaEstrazione.standard(CLOCK));

    private static byte[] pdf(String nome) throws IOException {
        try (InputStream in = FixtureLettoreCedoliniTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            return in.readAllBytes();
        }
    }

    private static byte[] pdfConTesto(String testo) throws IOException {
        try (var doc = new PDDocument()) {
            var pagina = new PDPage();
            doc.addPage(pagina);
            try (var contenuto = new PDPageContentStream(doc, pagina)) {
                contenuto.beginText();
                contenuto.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenuto.newLineAtOffset(50, 700);
                contenuto.showText(testo);
                contenuto.endText();
            }
            var uscita = new ByteArrayOutputStream();
            doc.save(uscita);
            return uscita.toByteArray();
        }
    }

    @Test
    void trovaLaFixtureDalNomeDelFileCaricato() throws IOException {
        var risultato = lettore.estrai(pdfConTesto("qualsiasi"), "C:\\cedolini\\rossi-alfa-2023-13.pdf");

        assertThat(risultato.modello()).isEqualTo("fixture");
        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.TREDICESIMA);
        assertThat(risultato.jsonOriginale()).contains("13ma Mensilita'");
    }

    @Test
    void trovaLaFixtureDallHashDelPdfDiProva() throws IOException {
        var risultato = lettore.estrai(pdf("rossi-alfa-2024-tfr01"), "file-rinominato.pdf");

        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.FINE_RAPPORTO);
        assertThat(risultato.cedolino().datiContrattuali().dataCessazione()).isEqualTo("2024-06-15");
    }

    @Test
    void trovaLaFixtureDalMarcatoreNelTesto() throws IOException {
        var risultato = lettore.estrai(pdfConTesto("Fixture: rossi-beta-2024-12"));

        assertThat(risultato.cedolino().contieneTredicesima()).isTrue();
        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
    }

    @Test
    void unPdfSenzaFixtureEUnDocumentoNonValido() throws IOException {
        var sconosciuto = pdfConTesto("Fixture: inesistente");

        assertThatThrownBy(() -> lettore.estrai(sconosciuto, "altro.pdf"))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));
        assertThatThrownBy(() -> lettore.estrai("non e' un pdf".getBytes()))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));
        assertThatThrownBy(() -> lettore.estrai(new byte[0]))
                .isInstanceOf(EstrazioneCedolinoException.class);
    }

    @Test
    void unPdfPuoEssereRegistratoSuUnaFixture() throws IOException {
        var qualsiasi = pdfConTesto("cedolino senza marcatore");
        lettore.registra(qualsiasi, "rossi2-beta-2025-02");

        var risultato = lettore.estrai(qualsiasi);

        assertThat(risultato.cedolino().dipendente().codiceFiscale()).isEqualTo("RSSMRA85C12F205M");
    }

    @Test
    void leFixtureDiTestCopronoIDocumentiNonValidiEIncoerenti() throws IOException {
        assertThatThrownBy(() -> lettore.estrai(pdf("test-due-cedolini")))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO);
                    assertThat(e.getMessage()).contains("2 cedolini");
                });
        assertThatThrownBy(() -> lettore.estrai(pdf("test-non-cedolino")))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));

        var incoerente = lettore.estrai(pdf("test-incoerente"));
        assertThat(incoerente.avvisi()).contains("Codice fiscale del dipendente formalmente non valido",
                "Totale competenze meno totale trattenute non corrisponde al netto");
        assertThat(incoerente.cedolino().confidenza().complessiva()).isLessThan(new BigDecimal("0.93"));
    }

    @Test
    void tutteLeFixtureSonoValideCoerentiEConPdfDistinti() throws Exception {
        var risorse = new PathMatchingResourcePatternResolver().getResources("classpath*:fixtures/cedolini/*.json");
        var competenze = new TreeMap<String, String>();
        var hash = new HashSet<String>();
        var problemi = new ArrayList<String>();
        int valide = 0;
        for (var risorsa : risorse) {
            var nome = risorsa.getFilename().replace(".json", "");
            var pdf = pdf(nome);
            assertThat(pdf).as("PDF di prova per " + nome).isNotNull();
            assertThat(hash.add(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(pdf)))).as("PDF distinti").isTrue();
            if (nome.startsWith("test-")) {
                continue;
            }
            var risultato = lettore.estrai(pdf, null);
            risultato.avvisi().stream().filter(a -> !a.startsWith("Nota estrazione")).forEach(a -> problemi.add(nome + ": " + a));
            var c = risultato.cedolino();
            competenze.put(nome, c.periodo().anno() + "-" + c.periodo().mese() + " " + c.periodo().tipo());
            valide++;
        }
        assertThat(problemi).isEmpty();
        assertThat(valide).isEqualTo(34);
        assertThat(competenze).containsEntry("rossi-alfa-2023-13", "2023-12 TREDICESIMA")
                .containsEntry("rossi-beta-2024-07-14", "2024-7 QUATTORDICESIMA")
                .containsEntry("rossi-alfa-2024-tfr01", "2024-7 FINE_RAPPORTO")
                .containsEntry("rossi-alfa-2024-tfr02", "2024-9 FINE_RAPPORTO")
                .containsEntry("rossi-beta-2025-03", "2025-3 ORDINARIA")
                .containsEntry("rossi-alfa-2024-03-aggiuntivo", "2024-3 AGGIUNTIVA")
                .doesNotContainKey("rossi-alfa-2023-05");
    }

    @Test
    void ilFondoTfrAl31DicembreQuadraConLeQuoteDellAnno() throws IOException {
        Map<String, BigDecimal> dicembre = Map.of(
                "fondo", lettore.estrai(pdf("rossi-alfa-2023-12")).cedolino().tfr().fondo31DicembreAnnoPrecedente(),
                "riv", lettore.estrai(pdf("rossi-alfa-2023-12")).cedolino().tfr().rivalutazione(),
                "imp", lettore.estrai(pdf("rossi-alfa-2023-12")).cedolino().tfr().impostaSostitutiva(),
                "quote", lettore.estrai(pdf("rossi-alfa-2023-13")).cedolino().tfr().quotaAnno());
        var fondo2024 = lettore.estrai(pdf("rossi-alfa-2024-01")).cedolino().tfr().fondo31DicembreAnnoPrecedente();

        assertThat(dicembre.get("fondo").add(dicembre.get("riv")).subtract(dicembre.get("imp")).add(dicembre.get("quote")))
                .isEqualByComparingTo(fondo2024);
    }
}
