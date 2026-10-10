package com.fcusumano.sam.cedolini.adapter.pdf;

import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Difesa dalle "bombe di decompressione": prima di aprire il PDF con PDFBox (che decomprime in memoria gli object
 * stream gia' durante il caricamento) scorre i flussi compressi con Flate (zlib) del file e ne misura la dimensione
 * decompressa senza conservarla, con memoria costante. Un flusso che supera il limite rende il PDF non leggibile.
 * <p>
 * I flussi non zlib (immagini JPEG, altri filtri) sono ignorati. Nei PDF cifrati i flussi sono illeggibili prima della
 * decifratura: per loro valgono solo l'intercettazione degli errori di memoria in {@link PdfBoxLettorePdf} e il limite
 * di dimensione del caricamento.
 */
final class ControlloDecompressione {

    private static final byte[] PAROLA_STREAM = {'s', 't', 'r', 'e', 'a', 'm'};
    private static final int BUFFER = 64 * 1024;

    private ControlloDecompressione() {
    }

    /**
     * @param limiteFlusso byte decompressi massimi di un singolo flusso
     * @throws PdfNonLeggibileException DANNEGGIATO se un flusso supera il limite
     */
    static void verifica(byte[] pdf, long limiteFlusso) {
        var buffer = new byte[BUFFER];
        for (int i = 0; i <= pdf.length - PAROLA_STREAM.length; i++) {
            if (inizioFlusso(pdf, i)) {
                int dati = i + PAROLA_STREAM.length;
                dati += pdf[dati] == '\r' && dati + 1 < pdf.length && pdf[dati + 1] == '\n' ? 2 : 1;
                if (dimensioneDecompressa(pdf, dati, limiteFlusso, buffer) > limiteFlusso) {
                    throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile");
                }
            }
        }
    }

    /** Parola chiave "stream" seguita da fine riga e non preceduta da "end". */
    private static boolean inizioFlusso(byte[] pdf, int posizione) {
        for (int j = 0; j < PAROLA_STREAM.length; j++) {
            if (pdf[posizione + j] != PAROLA_STREAM[j]) {
                return false;
            }
        }
        int dopo = posizione + PAROLA_STREAM.length;
        if (dopo >= pdf.length || (pdf[dopo] != '\n' && pdf[dopo] != '\r')) {
            return false;
        }
        return posizione < 3 || pdf[posizione - 3] != 'e' || pdf[posizione - 2] != 'n' || pdf[posizione - 1] != 'd';
    }

    /** Byte prodotti decomprimendo dalla posizione indicata, fermandosi appena si supera il limite; 0 se non zlib. */
    private static long dimensioneDecompressa(byte[] pdf, int inizio, long limite, byte[] buffer) {
        var inflater = new Inflater();
        try {
            inflater.setInput(pdf, inizio, pdf.length - inizio);
            long prodotti = 0;
            while (!inflater.finished() && prodotti <= limite) {
                int letti = inflater.inflate(buffer);
                if (letti == 0) {
                    break;
                }
                prodotti += letti;
            }
            return prodotti;
        } catch (DataFormatException e) {
            return 0;
        } finally {
            inflater.end();
        }
    }
}
