package com.fcusumano.sam.cedolini.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Lettura dei PDF: validita', limiti e decifratura con le password candidate (lotto, configurate, codici fiscali).
 * PDF generati al volo o fittizi delle fixture.
 */
class PdfBoxLettorePdfTest {

    private static final String CF = "RSSMRA80A01H501U";
    private static final Supplier<List<String>> NESSUN_CF = List::of;

    private static PdfBoxLettorePdf lettore(List<String> passwordConfigurate) {
        return new PdfBoxLettorePdf(new PdfProperties(passwordConfigurate, DataSize.ofMegabytes(32), 100,
                DataSize.ofMegabytes(100)));
    }

    static byte[] pdf(int pagine, String passwordUtente) throws IOException {
        try (var doc = new PDDocument()) {
            for (int i = 0; i < pagine; i++) {
                doc.addPage(new PDPage());
            }
            if (passwordUtente != null) {
                var policy = new StandardProtectionPolicy("proprietario", passwordUtente, new AccessPermission());
                policy.setEncryptionKeyLength(256);
                doc.protect(policy);
            }
            var uscita = new ByteArrayOutputStream();
            doc.save(uscita);
            return uscita.toByteArray();
        }
    }

    private static byte[] fixture(String nome) throws IOException {
        try (InputStream in = PdfBoxLettorePdfTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            return in.readAllBytes();
        }
    }

    @Test
    void unPdfNonProtettoVieneRestituitoInvariato() throws IOException {
        var originale = pdf(2, null);
        var chiamateCf = new AtomicInteger();

        var documento = lettore(List.of()).apri(originale, null, () -> {
            chiamateCf.incrementAndGet();
            return List.of(CF);
        });

        assertThat(documento.contenutoPerEstrazione()).isSameAs(originale);
        assertThat(documento.numeroPagine()).isEqualTo(2);
        assertThat(documento.protetto()).isFalse();
        assertThat(chiamateCf).as("i codici fiscali si leggono solo se servono").hasValue(0);
    }

    @Test
    void unFileCheNonEUnPdfVieneRifiutato() {
        assertThatThrownBy(() -> lettore(List.of()).apri("ciao".getBytes(StandardCharsets.UTF_8), null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> {
                    assertThat(e.causa()).isEqualTo(Causa.NON_PDF);
                    assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO);
                });
        assertThatThrownBy(() -> lettore(List.of()).apri(new byte[0], null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> assertThat(e.causa()).isEqualTo(Causa.NON_PDF));
    }

    @Test
    void unPdfDanneggiatoVieneRifiutato() {
        var rotto = "%PDF-1.7\n1 0 obj << /Type /Catalog >> garbage".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> lettore(List.of()).apri(rotto, null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> assertThat(e.causa()).isEqualTo(Causa.DANNEGGIATO));
    }

    @Test
    void unPdfProtettoSiApreConLaPasswordDelLottoESiOttieneUnaCopiaInChiaro() throws IOException {
        var originale = pdf(1, "segreta");
        var copiaOriginale = originale.clone();

        var documento = lettore(List.of()).apri(originale, "segreta", NESSUN_CF);

        assertThat(documento.protetto()).isTrue();
        assertThat(documento.numeroPagine()).isEqualTo(1);
        assertThat(documento.contenutoPerEstrazione()).isNotEqualTo(originale);
        try (var copia = Loader.loadPDF(documento.contenutoPerEstrazione())) {
            assertThat(copia.isEncrypted()).isFalse();
            assertThat(copia.getNumberOfPages()).isEqualTo(1);
        }
        assertThat(originale).as("l'originale non viene toccato").isEqualTo(copiaOriginale);
    }

    @Test
    void siProvanoLePasswordConfigurateEPoiICodiciFiscaliInMaiuscoloEMinuscolo() throws IOException {
        var conConfigurata = pdf(1, "aziendale-2024");
        assertThat(lettore(List.of("altra", "aziendale-2024")).apri(conConfigurata, "sbagliata", NESSUN_CF).protetto()).isTrue();

        var conCfMinuscolo = pdf(1, CF.toLowerCase());
        var documento = lettore(List.of("altra")).apri(conCfMinuscolo, null, () -> List.of("BNCLRA90B41F205X", CF));
        try (var copia = Loader.loadPDF(documento.contenutoPerEstrazione())) {
            assertThat(copia.isEncrypted()).isFalse();
        }

        var conCfMaiuscolo = pdf(1, CF);
        assertThat(lettore(List.of()).apri(conCfMaiuscolo, "", () -> List.of(CF.toLowerCase())).protetto()).isTrue();
    }

    @Test
    void senzaPasswordValidaIlMessaggioChiedeDiIndicarla() throws IOException {
        var protetto = pdf(1, "segreta");

        assertThatThrownBy(() -> lettore(List.of("altra")).apri(protetto, "sbagliata", () -> List.of(CF)))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> {
                    assertThat(e.causa()).isEqualTo(Causa.PROTETTO_DA_PASSWORD);
                    assertThat(e.getMessage()).isEqualTo("PDF protetto da password: riprova indicando la password");
                });
        assertThatThrownBy(() -> lettore(List.of()).apri(protetto, null, null))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class,
                        e -> assertThat(e.causa()).isEqualTo(Causa.PROTETTO_DA_PASSWORD));
    }

    @Test
    void limitiDiPagineEDimensioneDellApi() throws IOException {
        var tante = pdf(5, null);
        var pocheP = new PdfBoxLettorePdf(new PdfProperties(List.of(), DataSize.ofMegabytes(32), 4,
                DataSize.ofMegabytes(100)));
        assertThatThrownBy(() -> pocheP.apri(tante, null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> assertThat(e.causa()).isEqualTo(Causa.TROPPE_PAGINE));

        var piccola = new PdfBoxLettorePdf(new PdfProperties(List.of(), DataSize.ofKilobytes(256), 100,
                DataSize.ofMegabytes(100)));
        assertThatThrownBy(() -> piccola.apri(tante, null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> assertThat(e.causa()).isEqualTo(Causa.TROPPO_GRANDE));
    }

    @Test
    void ilPdfDiFixtureProtettoSiApreConLaPasswordDocumentata() throws IOException {
        var protetto = fixture("rossi-beta-2024-09");
        assertThatThrownBy(() -> Loader.loadPDF(protetto)).isInstanceOf(InvalidPasswordException.class);

        var documento = lettore(List.of()).apri(protetto, null, () -> List.of(CF));

        assertThat(documento.protetto()).isTrue();
        try (var copia = Loader.loadPDF(documento.contenutoPerEstrazione())) {
            assertThat(new PDFTextStripper().getText(copia)).contains("Cedolino di prova", "Fixture: rossi-beta-2024-09");
        }
    }

    /** PDF di una pagina il cui contenuto, compresso con Flate, si decomprime in {@code byteDecompressi} byte. */
    private static byte[] pdfConFlussoCompresso(int byteDecompressi) throws IOException {
        try (var doc = new PDDocument()) {
            var pagina = new PDPage();
            doc.addPage(pagina);
            var contenuto = new PDStream(doc, new ByteArrayInputStream(new byte[byteDecompressi]), COSName.FLATE_DECODE);
            pagina.setContents(contenuto);
            var uscita = new ByteArrayOutputStream();
            doc.save(uscita);
            return uscita.toByteArray();
        }
    }

    @Test
    void unFlussoCheSiDecomprimeOltreIlLimiteRendeIlPdfNonLeggibile() throws IOException {
        var bomba = pdfConFlussoCompresso(4 * 1024 * 1024);
        assertThat(bomba.length).as("file piccolo, contenuto enorme").isLessThan(64 * 1024);
        var limitato = new PdfBoxLettorePdf(new PdfProperties(List.of(), DataSize.ofMegabytes(32), 100,
                DataSize.ofMegabytes(1)));

        assertThatThrownBy(() -> limitato.apri(bomba, null, NESSUN_CF))
                .isInstanceOfSatisfying(PdfNonLeggibileException.class, e -> {
                    assertThat(e.causa()).isEqualTo(Causa.DANNEGGIATO);
                    assertThat(e.getMessage()).isEqualTo("Il PDF è danneggiato o non leggibile");
                });
        assertThat(lettore(List.of()).apri(bomba, null, NESSUN_CF).numeroPagine()).isEqualTo(1);
    }

    @Test
    void iFlussiEntroIlLimiteEQuelliNonZlibNonBloccanoIlPdf() throws IOException {
        var limitato = new PdfBoxLettorePdf(new PdfProperties(List.of(), DataSize.ofMegabytes(32), 100,
                DataSize.ofMegabytes(1)));

        assertThat(limitato.apri(pdfConFlussoCompresso(512 * 1024), null, NESSUN_CF).numeroPagine()).isEqualTo(1);
        assertThat(limitato.apri(fixture("rossi-beta-2024-09"), null, () -> List.of(CF)).protetto()).isTrue();
    }

    @Test
    void lePasswordConfigurateVuoteVengonoIgnorate() {
        var properties = new PdfProperties(Arrays.asList("", " ", null, "valida"), DataSize.ofMegabytes(32), 100,
                DataSize.ofMegabytes(100));

        assertThat(properties.passwordCandidate()).containsExactly("valida");
    }
}
