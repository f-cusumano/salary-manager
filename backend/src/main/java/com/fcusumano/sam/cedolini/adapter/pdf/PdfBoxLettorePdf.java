package com.fcusumano.sam.cedolini.adapter.pdf;

import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf.Sblocco;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * lettore e da archiviare. Sblocca anche i PDF gia' archiviati ({@link #sblocca}). Errori di memoria o di annidamento di un PDF costruito ad arte diventano "PDF danneggiato". Non logga mai
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
                             Supplier<? extends Collection<String>> passwordRicordate,
                             Supplier<? extends Collection<String>> codiciFiscaliCensiti) {
        verificaFile(contenuto);
        var utente = candidate(passwordUtente);
        try (var documento = apriConPassword(contenuto, List.of(utente, properties.passwordCandidate()),
                Arrays.asList(passwordRicordate, codiciFiscaliCensiti))) {
            int pagine = documento.documento().getNumberOfPages();
            if (pagine == 0) {
                throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF non contiene pagine");
            }
            if (pagine > properties.pagineMassime()) {
                throw new PdfNonLeggibileException(Causa.TROPPE_PAGINE, "Il PDF ha " + pagine
                        + " pagine: il massimo per l'estrazione è " + properties.pagineMassime());
            }
            boolean cifrato = documento.documento().isEncrypted();
            var perEstrazione = cifrato ? senzaProtezione(documento.documento()) : contenuto;
            verificaDimensione(perEstrazione);
            var passwordValida = documento.password() != null && utente.contains(documento.password())
                    ? documento.password() : null;
            return new DocumentoPdf(perEstrazione, pagine, documento.password() != null, cifrato, passwordValida);
        } catch (IOException e) {
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
        } catch (OutOfMemoryError | StackOverflowError e) {
            // PDF costruito ad arte (strutture troppo grandi o troppo annidate): la memoria si libera con il documento
            log.warn("PDF non leggibile: {}", e.getClass().getName());
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile");
        }
    }

    @Override
    public Sblocco sblocca(byte[] contenuto, Supplier<? extends Collection<String>> password) {
        try {
            verificaFile(contenuto);
            try (var documento = apriConPassword(contenuto, List.of(properties.passwordCandidate()),
                    Arrays.asList(password))) {
                if (!documento.documento().isEncrypted()) {
                    return new Sblocco.NonProtetto();
                }
                return new Sblocco.Sbloccato(senzaProtezione(documento.documento()));
            }
        } catch (PdfNonLeggibileException e) {
            return e.causa() == Causa.PROTETTO_DA_PASSWORD ? new Sblocco.Protetto() : new Sblocco.NonLeggibile();
        } catch (IOException e) {
            return new Sblocco.NonLeggibile();
        } catch (OutOfMemoryError | StackOverflowError e) {
            log.warn("PDF archiviato non sbloccabile: {}", e.getClass().getName());
            return new Sblocco.NonLeggibile();
        }
    }

    private void verificaFile(byte[] contenuto) {
        if (contenuto == null || contenuto.length == 0 || !haIntestazionePdf(contenuto)) {
            throw new PdfNonLeggibileException(Causa.NON_PDF, "Il file non è un PDF");
        }
        ControlloDecompressione.verifica(contenuto, properties.limiteDecompressione().toBytes());
    }

    /** Documento aperto e la password di apertura che e' servita (null se nessuna). */
    private record Aperto(PDDocument documento, String password) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            documento.close();
        }

        @Override
        public String toString() {
            return "Aperto[conPassword=" + (password != null) + "]";
        }
    }

    /**
     * Apre il PDF senza password o con la prima password valida: prima gli elenchi gia' noti, poi quelli dei fornitori
     * (invocati solo se servono), nell'ordine dato.
     */
    private Aperto apriConPassword(byte[] contenuto, List<List<String>> note,
                                   List<Supplier<? extends Collection<String>>> fornitori) {
        try {
            return new Aperto(Loader.loadPDF(contenuto), null);
        } catch (InvalidPasswordException e) {
            log.debug("PDF protetto da password di apertura: provo le password candidate");
        } catch (IOException | RuntimeException e) {
            throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
        }
        var provate = new LinkedHashSet<String>();
        Aperto aperto = null;
        for (var elenco : note) {
            aperto = aperto != null ? aperto : prova(contenuto, elenco, provate);
        }
        for (var fornitore : fornitori) {
            if (aperto == null && fornitore != null) {
                var password = fornitore.get();
                aperto = prova(contenuto, varianti(password == null ? List.of() : password), provate);
            }
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
                return new Aperto(Loader.loadPDF(contenuto, p), p);
            } catch (InvalidPasswordException e) {
                // password errata: si prova la successiva
            } catch (IOException | RuntimeException e) {
                throw new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile", e);
            }
        }
        return null;
    }

    /** Password indicata dall'utente e, se ha spazi ai lati, la sua versione senza. */
    private static List<String> candidate(String passwordUtente) {
        var candidate = new LinkedHashSet<String>();
        if (passwordUtente != null && !passwordUtente.isEmpty()) {
            candidate.add(passwordUtente);
            if (!passwordUtente.strip().equals(passwordUtente) && !passwordUtente.isBlank()) {
                candidate.add(passwordUtente.strip());
            }
        }
        return List.copyOf(candidate);
    }

    /**
     * Password dei fornitori: se sono codici fiscali (16 lettere e cifre) in maiuscolo e in minuscolo (le password dei
     * cedolini sono spesso il CF del dipendente), poi ciascuna com'e'.
     */
    private static List<String> varianti(Collection<String> password) {
        var varianti = new LinkedHashSet<String>();
        for (var p : password) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            var pulito = p.strip();
            if (pulito.matches("[A-Za-z0-9]{16}")) {
                varianti.add(pulito.toUpperCase(Locale.ROOT));
                varianti.add(pulito.toLowerCase(Locale.ROOT));
            }
            varianti.add(p);
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
