package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Carattere;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Pagina;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Lettura del testo di un PDF con le posizioni dei caratteri (Apache PDFBox). Il PDF deve essere in chiaro: quelli
 * protetti sono gia' stati decifrati da {@code LettorePdf}. Le immagini (scansioni) non producono testo.
 */
final class TestoPdf {

    /** Pagine lette al massimo: un cedolino ne occupa poche, il resto non serve al riconoscimento. */
    static final int PAGINE_MASSIME = 20;

    private TestoPdf() {
    }

    /**
     * @throws IOException se il PDF non e' leggibile
     */
    static DocumentoTesto leggi(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int pagine = Math.min(documento.getNumberOfPages(), PAGINE_MASSIME);
            var lette = new ArrayList<Pagina>();
            for (int numero = 1; numero <= pagine; numero++) {
                var caratteri = new ArrayList<Carattere>();
                var raccoglitore = new Raccoglitore(caratteri);
                raccoglitore.setSortByPosition(true);
                raccoglitore.setStartPage(numero);
                raccoglitore.setEndPage(numero);
                raccoglitore.getText(documento);
                var box = documento.getPage(numero - 1).getCropBox();
                boolean ruotata = documento.getPage(numero - 1).getRotation() % 180 != 0;
                lette.add(new Pagina(numero, ruotata ? box.getHeight() : box.getWidth(),
                        ruotata ? box.getWidth() : box.getHeight(), caratteri));
            }
            return new DocumentoTesto(lette);
        }
    }

    /** Raccoglie i caratteri con le coordinate gia' corrette per la rotazione del testo. */
    private static final class Raccoglitore extends PDFTextStripper {

        private final List<Carattere> caratteri;

        Raccoglitore(List<Carattere> caratteri) {
            this.caratteri = caratteri;
        }

        @Override
        protected void writeString(String testo, List<TextPosition> posizioni) {
            for (var p : posizioni) {
                var unicode = p.getUnicode();
                if (unicode == null || unicode.isEmpty()) {
                    continue;
                }
                caratteri.add(new Carattere(unicode, p.getXDirAdj(), p.getWidthDirAdj(), p.getYDirAdj(),
                        p.getHeightDir()));
            }
        }
    }
}
