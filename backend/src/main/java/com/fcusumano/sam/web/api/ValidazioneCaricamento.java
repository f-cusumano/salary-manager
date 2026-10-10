package com.fcusumano.sam.web.api;

import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.web.error.FileTroppoGrandeException;
import com.fcusumano.sam.web.error.LimitiCaricamento;
import com.fcusumano.sam.web.error.RichiestaNonValidaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validazione sincrona dell'upload, prima di registrare il lotto: almeno un file, nessun file vuoto o oltre il limite,
 * solo PDF (estensione {@code .pdf} o tipo {@code application/pdf}, e intestazione {@code %PDF-} nei primi 1024 byte,
 * come richiesto dal lettore PDF del modulo cedolini). Un lotto con file non validi viene rifiutato per intero, con
 * l'elenco dei file da togliere.
 * <p>
 * La password del lotto non compare mai nei messaggi di errore.
 */
@Component
class ValidazioneCaricamento {

    static final int LUNGHEZZA_MASSIMA_PASSWORD = 200;

    private static final byte[] INTESTAZIONE_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** Byte iniziali in cui cercare l'intestazione (alcuni generatori la precedono con byte spuri). */
    private static final int AREA_INTESTAZIONE = 1024;
    private static final String TIPO_PDF_ALTERNATIVO = "application/x-pdf";

    private final DataSize dimensioneMassima;

    ValidazioneCaricamento(@Value(LimitiCaricamento.MAX_FILE) DataSize dimensioneMassima) {
        this.dimensioneMassima = dimensioneMassima;
    }

    /**
     * @return i file da passare al modulo cedolini, nell'ordine ricevuto
     * @throws RichiestaNonValidaException se manca un file, se un file e' vuoto o non e' un PDF
     * @throws FileTroppoGrandeException   se un file supera la dimensione massima
     */
    List<FileCaricato> fileCaricati(List<MultipartFile> files) {
        var presenti = files == null ? List.<MultipartFile>of() : files.stream()
                .filter(file -> file != null && !(file.isEmpty() && nome(file).isEmpty()))
                .toList();
        if (presenti.isEmpty()) {
            throw new RichiestaNonValidaException("Seleziona almeno un PDF da caricare (campo \"files\").");
        }
        var troppoGrandi = presenti.stream().filter(f -> f.getSize() > dimensioneMassima.toBytes()).toList();
        if (!troppoGrandi.isEmpty()) {
            var verbo = troppoGrandi.size() == 1 ? " supera" : " superano";
            throw new FileTroppoGrandeException(elenco(troppoGrandi) + verbo + " la dimensione massima di "
                    + LimitiCaricamento.descrivi(dimensioneMassima) + " per file.");
        }
        var vuoti = presenti.stream().filter(MultipartFile::isEmpty).toList();
        if (!vuoti.isEmpty()) {
            throw new RichiestaNonValidaException(elenco(vuoti) + (vuoti.size() == 1 ? " è vuoto." : " sono vuoti."));
        }
        var caricati = new ArrayList<FileCaricato>(presenti.size());
        var nonPdf = new ArrayList<MultipartFile>();
        for (var file : presenti) {
            var contenuto = contenuto(file);
            if (dichiaratoPdf(file) && haIntestazionePdf(contenuto)) {
                caricati.add(new FileCaricato(file.getOriginalFilename(), contenuto));
            } else {
                nonPdf.add(file);
            }
        }
        if (!nonPdf.isEmpty()) {
            throw new RichiestaNonValidaException("Sono ammessi solo file PDF: " + nomi(nonPdf)
                    + (nonPdf.size() == 1 ? " non è un PDF valido." : " non sono PDF validi."));
        }
        return caricati;
    }

    /**
     * Password del lotto normalizzata: null se assente o vuota.
     *
     * @throws RichiestaNonValidaException se supera la lunghezza massima (il valore non e' riportato)
     */
    static String password(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        if (password.length() > LUNGHEZZA_MASSIMA_PASSWORD) {
            throw new RichiestaNonValidaException(
                    "La password dei PDF può essere lunga al massimo " + LUNGHEZZA_MASSIMA_PASSWORD + " caratteri.");
        }
        return password;
    }

    private static boolean dichiaratoPdf(MultipartFile file) {
        if (nome(file).toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return true;
        }
        var tipo = file.getContentType();
        if (tipo == null) {
            return false;
        }
        try {
            var mediaType = MediaType.parseMediaType(tipo);
            return mediaType.isCompatibleWith(MediaType.APPLICATION_PDF)
                    || mediaType.isCompatibleWith(MediaType.parseMediaType(TIPO_PDF_ALTERNATIVO));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean haIntestazionePdf(byte[] contenuto) {
        int limite = Math.min(contenuto.length, AREA_INTESTAZIONE) - INTESTAZIONE_PDF.length;
        for (int inizio = 0; inizio <= limite; inizio++) {
            if (Arrays.equals(contenuto, inizio, inizio + INTESTAZIONE_PDF.length,
                    INTESTAZIONE_PDF, 0, INTESTAZIONE_PDF.length)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] contenuto(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new RichiestaNonValidaException("Impossibile leggere il file " + virgolette(file)
                    + ": ricaricalo.");
        }
    }

    private static String elenco(List<MultipartFile> files) {
        return (files.size() == 1 ? "Il file " : "I file ") + nomi(files);
    }

    private static String nomi(List<MultipartFile> files) {
        return files.stream().map(ValidazioneCaricamento::virgolette).collect(Collectors.joining(", "));
    }

    private static String virgolette(MultipartFile file) {
        var nome = nome(file);
        return "\"" + (nome.isEmpty() ? "senza nome" : nome) + "\"";
    }

    /** Nome del file senza percorso (alcuni browser lo includono). */
    private static String nome(MultipartFile file) {
        var originale = file.getOriginalFilename();
        if (originale == null) {
            return "";
        }
        var percorso = originale.replace('\\', '/');
        return percorso.substring(percorso.lastIndexOf('/') + 1).strip();
    }
}
