package com.fcusumano.sam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fcusumano.sam.shared.config.CaricatoreFileEnv;
import com.fcusumano.sam.shared.config.VariabiliAmbiente;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Il file {@code .env} indicato da {@code SAM_ENV_FILE} (di default quello della radice del progetto) entra
 * nell'ambiente Spring tramite l'import di {@code application.yml}: le sue variabili risolvono i segnaposto della
 * configurazione e {@code ANTHROPIC_API_KEY} arriva a {@link VariabiliAmbiente}. Le verifiche guardano le sorgenti
 * {@code .env} per nome o usano variabili dedicate, cosi' l'esito non dipende dalle variabili d'ambiente della
 * macchina; i controlli sui valori risolti che una variabile d'ambiente vera cambierebbe sono saltati se c'e'.
 */
@SpringBootTest(classes = ConfigurazioneFileEnvTest.Vuota.class, webEnvironment = WebEnvironment.NONE,
        properties = {"SAM_ENV_FILE=src/test/resources/env/prova.env",
                "prova.segnaposto=${SAM_PROVA_SEGNAPOSTO:assente}", "prova.letterale=${SAM_PROVA_LETTERALE:assente}",
                "prova.dollaro=${SAM_PROVA_DOLLARO:assente}", "prova.vuota=${SAM_PROVA_VUOTA:default}"})
@ActiveProfiles("test")
class ConfigurazioneFileEnvTest {

    @Autowired
    ConfigurableEnvironment ambiente;

    @Test
    void ilFileDiventaUnaSorgenteDotenvConLaSintassiDiCompose() {
        var sorgente = sorgente("prova.env]");

        assertThat(sorgente.getProperty("ANTHROPIC_API_KEY")).isEqualTo("sk-ant-chiave-inventata");
        assertThat(sorgente.getProperty("SAM_AI_MODEL")).isEqualTo("claude-modello-di-prova");
    }

    @Test
    void leVariabiliDelFileRisolvonoISegnaposto() {
        assertThat(ambiente.getProperty("prova.segnaposto")).isEqualTo("valore-dal-file-env");
    }

    @Test
    void iValoriArrivanoAiSegnapostoComeConDockerCompose() {
        assertThat(ambiente.getProperty("prova.letterale")).isEqualTo("utente${SAM_PROVA_SEGNAPOSTO}");
        assertThat(Binder.get(ambiente).bind("prova.letterale", String.class).get())
                .isEqualTo("utente${SAM_PROVA_SEGNAPOSTO}");
        assertThat(ambiente.getProperty("prova.dollaro")).isEqualTo("Pa$w0rd");
        assertThat(ambiente.getProperty("prova.vuota")).isEqualTo("default");
        assertThat(VariabiliAmbiente.leggi(ambiente, "SAM_PROVA_LETTERALE"))
                .contains("utente${SAM_PROVA_SEGNAPOSTO}");
    }

    @Test
    void unaVariabileVuotaNelFileLasciaIlDefaultDiApplicationYml() {
        assumeTrue(System.getenv("SAM_SERVER_ADDRESS") == null && System.getProperty("SAM_SERVER_ADDRESS") == null,
                "SAM_SERVER_ADDRESS impostata nell'ambiente: ha la precedenza sul file .env");

        assertThat(ambiente.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void leVariabiliSamDelFileRisolvonoISegnapostoDiApplicationYml() {
        assumeTrue(System.getenv("SAM_AI_MODEL") == null && System.getProperty("SAM_AI_MODEL") == null,
                "SAM_AI_MODEL impostata nell'ambiente: ha la precedenza sul file .env");

        assertThat(ambiente.getProperty("salary-manager.ai.model")).isEqualTo("claude-modello-di-prova");
    }

    @Test
    void laVarianteDelProfiloAttivoPrecedeIlFileBase() {
        var nomi = sorgentiDotenv().stream().map(PropertySource::getName).toList();

        assertThat(nomi).hasSize(2);
        assertThat(nomi.get(0)).contains("prova.env-test]");
        assertThat(nomi.get(1)).contains("prova.env]");
        assertThat(ambiente.getProperty("SAM_PROVA_VARIANTE")).isEqualTo("dalla-variante-test");
    }

    @Test
    void laChiaveApiArrivaDalFileSeLAmbienteNonLaDefinisce() {
        var daAmbiente = System.getenv("ANTHROPIC_API_KEY");
        assumeTrue(daAmbiente == null || daAmbiente.isBlank(),
                "ANTHROPIC_API_KEY impostata nell'ambiente: ha la precedenza sul file .env");

        assertThat(VariabiliAmbiente.leggi(ambiente, "ANTHROPIC_API_KEY")).contains("sk-ant-chiave-inventata");
    }

    private List<PropertySource<?>> sorgentiDotenv() {
        return ambiente.getPropertySources().stream()
                .filter(s -> s.getName().startsWith(CaricatoreFileEnv.PREFISSO_SORGENTE))
                .toList();
    }

    private PropertySource<?> sorgente(String fineNomeFile) {
        return sorgentiDotenv().stream()
                .filter(s -> s.getName().contains(fineNomeFile))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nessuna sorgente .env per " + fineNomeFile));
    }

    /** Contesto vuoto (interessa solo l'ambiente); essendo interna a un test non entra nella scansione degli altri. */
    @Configuration(proxyBeanMethods = false)
    static class Vuota {
    }
}
