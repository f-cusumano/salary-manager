package com.fcusumano.sam.cedolini.adapter.pdf;

import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link LettorePdf} basato su Apache PDFBox 3: verifica l'intestazione e l'assenza di flussi compressi abnormi
 * ({@link ControlloDecompressione}), apre il documento (anche protetto, provando le password candidate), conta le
 * pagine, controlla i limiti dell'API e, se il PDF e' cifrato, ne produce una copia senza protezione da inviare al
 * modello. Errori di memoria o di annidamento di un PDF costruito ad arte diventano "PDF danneggiato". Non logga mai
 * password ne' contenuti.
 */
@Component
class PdfBoxLettorePdf implements LettorePdf {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxLettorePdf.class);

    private static final byte[] INTESTAZIONE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** Byte iniziali in cui cercare l'intestazione (alcuni generatori la precedono con byte spuri). */
    private static final int AREA_INTESTAZIONE = 1024;
    /** Spazio riservato nella richiesta a prompt di sistema, schema e testo. */
    private static final long MARGINE_RICHIESTA = 256 * 1024;

    private final PdfProperties properties;

    PdfBoxLettorePdf(PdfProperties properties) {
        this.properties = properties;
    }

    @Override
    public DocumentoPdf apri(byte[] contenuto, String passwordUtente,
                             Supplier<? extends Collection<String>> codiciFiscaliCensiti) {
        if (contenuto == null || contenuto.length == 0 || !haIntestazionePdf(contenuto)) {
            throw new PdfNonLeggibileException(Causa.NON_PDF, "Il file non è un PDF");
        }
        ControlloDecompressione.verifica(contenuto, properties.limiteDecompressione().toBytes());
        try (var documento = apriConPassword(contenuto, passwordUtente, codiciFiscaliCensiti)) {
            int pagine = documento.documento().getNumberOfPages();
            if (pagine == 0) {
                throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF non contiene pagine");
            }
            if (pagine > properties.pagineMassime()) {
                throw new PdfNonLeggibileException(Causa.TROPPE_PAGINE, "Il PDF ha " + pagine
                        + " pagine: il massimo per l'estrazione è " + properties.pagineMassime());
            }
            var perEstrazione = documento.documento().isEncrypted() ? senzaProtezione(documento.documento()) : contenuto;
            verificaDimensione(perEstrazione);
            return new DocumentoPdf(perEstrazione, pagine, documento.conPassword());
        } catch (IOException e) {
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
        } catch (OutOfMemoryError | StackOverflowError e) {
            // PDF costruito ad arte (strutture troppo grandi o troppo annidate): la memoria si libera con il documento
            log.warn("PDF non leggibile: {}", e.getClass().getName());
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile");
        }
    }

    /** Documento aperto e se e' servita una password di apertura. */
    private record Aperto(PDDocument documento, boolean conPassword) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            documento.close();
        }
    }

    private Aperto apriConPassword(byte[] contenuto, String passwordUtente,
                                   Supplier<? extends Collection<String>> codiciFiscaliCensiti) {
        try {
            return new Aperto(Loader.loadPDF(contenuto), false);
        } catch (InvalidPasswordException e) {
            log.debug("PDF protetto da password di apertura: provo le password candidate");
        } catch (IOException | RuntimeException e) {
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
        }
        var provate = new LinkedHashSet<String>();
        var aperto = prova(contenuto, candidate(passwordUtente, properties.passwordCandidate()), provate);
        if (aperto == null && codiciFiscaliCensiti != null) {
            var codici = codiciFiscaliCensiti.get();
            aperto = prova(contenuto, varianti(codici == null ? List.of() : codici), provate);
        }
        if (aperto == null) {
            log.info("PDF protetto: nessuna delle {} password candidate e' valida", provate.size());
            throw new PdfNonLeggibileException(Causa.PROTETTO_DA_PASSWORD, PdfNonLeggibileException.MESSAGGIO_PASSWORD);
        }
        return aperto;
    }

    private static Aperto prova(byte[] contenuto, Collection<String> password, Set<String> provate) {
        for (var p : password) {
            if (!provate.add(p)) {
                continue;
            }
            try {
                return new Aperto(Loader.loadPDF(contenuto, p), true);
            } catch (InvalidPasswordException e) {
                // password errata: si prova la successiva
            } catch (IOException | RuntimeException e) {
                throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
            }
        }
        return null;
    }

    private static List<String> candidate(String passwordUtente, List<String> configurate) {
        var candidate = new LinkedHashSet<String>();
        if (passwordUtente != null && !passwordUtente.isEmpty()) {
            candidate.add(passwordUtente);
            if (!passwordUtente.strip().equals(passwordUtente) && !passwordUtente.isBlank()) {
                candidate.add(passwordUtente.strip());
            }
        }
        candidate.addAll(configurate);
        return List.copyOf(candidate);
    }

    /** Codici fiscali in maiuscolo e in minuscolo (le password dei cedolini sono spesso il CF del dipendente). */
    private static List<String> varianti(Collection<String> codiciFiscali) {
        var varianti = new LinkedHashSet<String>();
        for (var cf : codiciFiscali) {
            if (cf != null && !cf.isBlank()) {
                var pulito = cf.strip();
                varianti.add(pulito.toUpperCase(Locale.ROOT));
                varianti.add(pulito.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(varianti);
    }

    private static byte[] senzaProtezione(PDDocument documento) throws IOException {
        documento.setAllSecurityToBeRemoved(true);
        var uscita = new ByteArrayOutputStream();
        documento.save(uscita);
        return uscita.toByteArray();
    }

    private void verificaDimensione(byte[] perEstrazione) {
        long base64 = 4L * ((perEstrazione.length + 2L) / 3L);
        if (base64 + MARGINE_RICHIESTA > properties.dimensioneMassimaRichiesta().toBytes()) {
            throw new PdfNonLeggibileException(Causa.TROPPO_GRANDE, "Il PDF è troppo grande per l'estrazione (massimo "
                    + massimoPdfMegabyte() + " MB)");
        }
    }

    private long massimoPdfMegabyte() {
        long massimo = (properties.dimensioneMassimaRichiesta().toBytes() - MARGINE_RICHIESTA) / 4L * 3L;
        return Math.max(0, massimo / (1024 * 1024));
    }

    private static boolean haIntestazionePdf(byte[] contenuto) {
        int limite = Math.min(contenuto.length, AREA_INTESTAZIONE) - INTESTAZIONE.length;
        for (int i = 0; i <= limite; i++) {
            boolean trovata = true;
            for (int j = 0; j < INTESTAZIONE.length && trovata; j++) {
                trovata = contenuto[i + j] == INTESTAZIONE[j];
            }
            if (trovata) {
                return true;
            }
        }
        return false;
    }
}
