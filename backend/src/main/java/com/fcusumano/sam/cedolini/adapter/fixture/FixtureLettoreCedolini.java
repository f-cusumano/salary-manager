package com.fcusumano.sam.cedolini.adapter.fixture;

import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.config.LettoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Lettore fake per test, sviluppo offline e modalita' demo ({@code salary-manager.lettore=fixture}): restituisce il
 * cedolino letto dal JSON di fixture associato al PDF, facendolo passare dalla stessa validazione degli altri lettori
 * ({@link InterpreteRispostaEstrazione}). Le fixture contengono solo dati inventati.
 * <p>
 * Il JSON e' {@code <fixture-dir>/<nome>.json} ({@code salary-manager.fixture-dir}, default
 * {@code classpath:fixtures/cedolini/}), nel formato dello schema {@code prompts/cedolino-schema-v1.json}. Il
 * {@code <nome>} si determina, in ordine:
 * <ol>
 *   <li>dal nome del file caricato senza estensione, se esiste la fixture omonima;</li>
 *   <li>dall'hash SHA-256 del PDF, confrontato con i PDF di fixture ({@code <fixture-dir>/*.pdf} e
 *       {@code <cartella superiore>/pdf/*.pdf}, es. {@code fixtures/pdf/}) che hanno lo stesso nome base;</li>
 *   <li>dalla riga {@code Fixture: <nome>} nel testo del PDF (vale anche per la copia decifrata di un PDF protetto).</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "salary-manager.lettore", havingValue = "fixture")
class FixtureLettoreCedolini implements LettoreCedolini {

    private static final Logger log = LoggerFactory.getLogger(FixtureLettoreCedolini.class);

    static final String MODELLO = "fixture";
    private static final Pattern MARCATORE = Pattern.compile("Fixture:\\s*([A-Za-z0-9._-]+)");

    private final String cartella;
    private final ResourcePatternResolver risorse;
    private final InterpreteRispostaEstrazione interprete;
    private final Map<String, String> fixturePerHash = new ConcurrentHashMap<>();
    private volatile boolean indicizzato;

    @Autowired
    FixtureLettoreCedolini(LettoreProperties properties, Clock clock) {
        this(properties.fixtureDir(), new PathMatchingResourcePatternResolver(), InterpreteRispostaEstrazione.standard(clock));
    }

    FixtureLettoreCedolini(String cartella, ResourcePatternResolver risorse, InterpreteRispostaEstrazione interprete) {
        this.cartella = cartella.endsWith("/") ? cartella : cartella + "/";
        this.risorse = risorse;
        this.interprete = interprete;
    }

    @Override
    public RisultatoEstrazione estrai(byte[] pdf) {
        return estrai(pdf, null);
    }

    @Override
    public RisultatoEstrazione estrai(byte[] pdf, String nomeFileOriginale) {
        if (pdf == null || pdf.length == 0) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO, "Documento vuoto");
        }
        var nome = perNomeFile(nomeFileOriginale)
                .or(() -> Optional.ofNullable(indice().get(sha256(pdf))))
                .or(() -> perMarcatore(pdf))
                .orElseThrow(() -> new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                        "Nessuna fixture di estrazione associata al PDF"));
        log.debug("Estrazione da fixture {}", nome);
        return interprete.interpreta(json(nome).orElseThrow(), MODELLO);
    }

    /** Associa esplicitamente un PDF a una fixture (test). */
    void registra(byte[] pdf, String nomeFixture) {
        fixturePerHash.put(sha256(pdf), nomeFixture);
    }

    private Optional<String> perNomeFile(String nomeFileOriginale) {
        if (nomeFileOriginale == null || nomeFileOriginale.isBlank()) {
            return Optional.empty();
        }
        var percorso = nomeFileOriginale.replace('\\', '/');
        var base = nomeBase(percorso.substring(percorso.lastIndexOf('/') + 1));
        return json(base).map(j -> base);
    }

    private Optional<String> perMarcatore(byte[] pdf) {
        try (var documento = Loader.loadPDF(pdf)) {
            var matcher = MARCATORE.matcher(new PDFTextStripper().getText(documento));
            return matcher.find() ? Optional.of(matcher.group(1)).filter(n -> json(n).isPresent()) : Optional.empty();
        } catch (IOException e) {
            log.debug("PDF non leggibile per la ricerca del marcatore di fixture: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> json(String nome) {
        if (!nome.matches("[A-Za-z0-9._-]+")) {
            return Optional.empty();
        }
        try {
            for (var risorsa : risorse.getResources(comePattern(cartella) + nome + ".json")) {
                if (risorsa.exists()) {
                    return Optional.of(risorsa.getContentAsString(StandardCharsets.UTF_8));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Fixture non leggibile: " + nome, e);
        }
    }

    private Map<String, String> indice() {
        if (!indicizzato) {
            synchronized (this) {
                if (!indicizzato) {
                    indicizza(comePattern(cartella) + "*.pdf");
                    indicizza(comePattern(cartellaPdf()) + "*.pdf");
                    indicizzato = true;
                }
            }
        }
        return fixturePerHash;
    }

    private void indicizza(String pattern) {
        try {
            for (Resource pdf : risorse.getResources(pattern)) {
                var nomeFile = pdf.getFilename();
                if (nomeFile != null && pdf.isReadable()) {
                    fixturePerHash.putIfAbsent(sha256(pdf.getContentAsByteArray()), nomeBase(nomeFile));
                }
            }
        } catch (IOException e) {
            log.warn("PDF di fixture non indicizzabili ({}): {}", pattern, e.getClass().getSimpleName());
        }
    }

    /** Cartella dei PDF di fixture: sorella di quella dei JSON ({@code fixtures/cedolini/} -> {@code fixtures/pdf/}). */
    private String cartellaPdf() {
        var senzaBarra = cartella.substring(0, cartella.length() - 1);
        int separatore = Math.max(senzaBarra.lastIndexOf('/'), senzaBarra.lastIndexOf(':'));
        return senzaBarra.substring(0, separatore + 1) + "pdf/";
    }

    /** Su classpath si cercano tutte le radici (classi e risorse di test). */
    private static String comePattern(String posizione) {
        return posizione.startsWith("classpath:") ? "classpath*:" + posizione.substring("classpath:".length()) : posizione;
    }

    private static String nomeBase(String nomeFile) {
        int punto = nomeFile.lastIndexOf('.');
        return punto > 0 ? nomeFile.substring(0, punto) : nomeFile;
    }

    private static String sha256(byte[] contenuto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenuto));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
