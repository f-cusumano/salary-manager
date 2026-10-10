package com.fcusumano.sam.cedolini.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.config.CampioniProperties;
import com.fcusumano.sam.shared.config.VariabiliAmbiente;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Estrazione reale con Claude dei cedolini in {@code salary-manager.samples-dir} (dati personali, esclusi da git;
 * piu' cartelle separate da {@code :}).
 * Escluso dalla build di default (property Maven {@code sam.test.excludedGroups=live,campioni}); si esegue dalla cartella
 * {@code backend/} con {@code ./mvnw test -Dgroups=live -Dsam.test.excludedGroups=none} e {@code ANTHROPIC_API_KEY}
 * nell'ambiente o nel file {@code .env} della radice del progetto: a differenza degli altri test, questo legge il
 * {@code .env} (e, con il profilo test, l'eventuale {@code .env-test}). Senza chiave API o senza campioni il test viene
 * saltato. Password dei PDF protetti: {@code SAM_SAMPLES_PASSWORD} (password del "lotto"), letta come la chiave con
 * {@link VariabiliAmbiente}, o {@code SAM_PDF_PASSWORD_CANDIDATE}.
 * <p>
 * Stampa per ogni file solo un numero progressivo, esito, tipo, competenza e confidenza: mai nomi file, nomi,
 * codici fiscali o importi. Degli errori stampa il motivo o solo la classe dell'eccezione, mai il messaggio (potrebbe
 * contenere dati del cedolino o un percorso con il nome del file).
 */
@Tag("live")
@SpringBootTest(properties = {"salary-manager.lettore=claude", "SAM_ENV_FILE=../.env"})
@ActiveProfiles("test")
class EstrazioneCampioniLiveTest {

    @Autowired
    LettoreCedolini lettore;

    @Autowired
    LettorePdf lettorePdf;

    @Autowired
    CampioniProperties campioni;

    @Autowired
    ConfigurableEnvironment ambiente;

    @Test
    void estraeICedoliniDiEsempio() throws IOException {
        assumeTrue(VariabiliAmbiente.leggi(ambiente, FornitoreClientAnthropic.VARIABILE_CHIAVE).isPresent(),
                FornitoreClientAnthropic.VARIABILE_CHIAVE + " non impostata (ambiente o .env): test live saltato");
        var pdf = new ArrayList<Path>();
        for (var cartella : campioni.cartelle()) {
            if (!Files.isDirectory(cartella)) {
                continue;
            }
            try (var file = Files.list(cartella)) {
                file.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")).sorted()
                        .forEach(pdf::add);
            }
        }
        assumeTrue(!pdf.isEmpty(), "Nessun PDF nelle cartelle dei campioni");
        var password = VariabiliAmbiente.leggi(ambiente, "SAM_SAMPLES_PASSWORD").orElse(null);

        int riusciti = 0;
        for (int i = 0; i < pdf.size(); i++) {
            try {
                var documento = lettorePdf.apri(Files.readAllBytes(pdf.get(i)), password, List::of);
                var c = lettore.estrai(documento.contenutoPerEstrazione()).cedolino();
                System.out.printf("#%02d OK tipo=%s competenza=%s-%s confidenza=%s protetto=%s%n", i + 1,
                        c.periodo().tipo(), c.periodo().anno(), c.periodo().mese(),
                        c.confidenza() == null ? "-" : c.confidenza().complessiva(), documento.protetto());
                riusciti++;
            } catch (EstrazioneCedolinoException e) {
                System.out.printf("#%02d ERRORE motivo=%s%n", i + 1, e.motivo());
            } catch (IOException | RuntimeException e) {
                System.out.printf("#%02d ERRORE %s%n", i + 1, e.getClass().getSimpleName());
            }
        }
        assertThat(riusciti).isPositive();
    }
}
