package com.fcusumano.sam.cedolini.adapter.locale;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/**
 * PDF sintetici per i test del lettore locale: testi posizionati con la linea di base misurata dall'alto della pagina
 * (come le coordinate lette da PDFBox), cosi' i layout si riproducono con le stesse posizioni dei cedolini veri. Solo
 * dati inventati. Il costruttore segnala i testi sovrapposti sulla stessa riga, che renderebbero il test ambiguo.
 */
final class PdfSintetico {

    static final PDFont HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont COURIER = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private record Testo(float x, float y, PDFont font, float dimensione, String testo) {

        float x1() {
            try {
                return x + font.getStringWidth(testo) / 1000 * dimensione;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private record Foglio(PDRectangle formato, List<Testo> testi, List<float[]> immagini) {
    }

    private final List<Foglio> fogli = new ArrayList<>();
    private PDFont font = HELVETICA;
    private float dimensione = 7;

    /** Nuova pagina A4. */
    PdfSintetico pagina() {
        return pagina(PDRectangle.A4);
    }

    PdfSintetico pagina(PDRectangle formato) {
        fogli.add(new Foglio(formato, new ArrayList<>(), new ArrayList<>()));
        return this;
    }

    /** Carattere dei testi successivi. */
    PdfSintetico carattere(PDFont font, float dimensione) {
        this.font = font;
        this.dimensione = dimensione;
        return this;
    }

    /** Testo con l'inizio in x e la linea di base a y punti dall'alto della pagina. */
    PdfSintetico testo(float x, float y, String testo) {
        var nuovo = new Testo(x, y, font, dimensione, testo);
        var foglio = fogli.getLast();
        for (var t : foglio.testi()) {
            if (Math.abs(t.y() - y) < 1.5f && nuovo.x() < t.x1() + 1 && t.x() < nuovo.x1() + 1) {
                throw new IllegalStateException("Testi sovrapposti a y=" + y + ": '" + t.testo() + "' e '" + testo + "'");
            }
        }
        foglio.testi().add(nuovo);
        return this;
    }

    /** Testo allineato a destra: finisce in x1. */
    PdfSintetico aDestra(float x1, float y, String testo) {
        try {
            return testo(x1 - font.getStringWidth(testo) / 1000 * dimensione, y, testo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Immagine grigia che copre l'area indicata (simula una scansione). */
    PdfSintetico immagine(float x, float y, float larghezza, float altezza) {
        fogli.getLast().immagini().add(new float[] {x, y, larghezza, altezza});
        return this;
    }

    byte[] pdf() {
        try (var documento = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (var foglio : fogli) {
                var pagina = new PDPage(foglio.formato());
                documento.addPage(pagina);
                float altezza = foglio.formato().getHeight();
                try (var contenuto = new PDPageContentStream(documento, pagina)) {
                    for (var area : foglio.immagini()) {
                        var grigio = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
                        for (int i = 0; i < 40; i++) {
                            for (int j = 0; j < 40; j++) {
                                grigio.setRGB(i, j, (i + j) % 2 == 0 ? 0x808080 : 0xC0C0C0);
                            }
                        }
                        var immagine = LosslessFactory.createFromImage(documento, grigio);
                        contenuto.drawImage(immagine, area[0], altezza - area[1] - area[3], area[2], area[3]);
                    }
                    for (var t : foglio.testi()) {
                        contenuto.beginText();
                        contenuto.setFont(t.font(), t.dimensione());
                        contenuto.newLineAtOffset(t.x(), altezza - t.y());
                        contenuto.showText(t.testo());
                        contenuto.endText();
                    }
                }
            }
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
