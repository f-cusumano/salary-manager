package com.fcusumano.sam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fcusumano.sam.shared.config.CaricatoreFileEnv;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Interfaccia di ascolto del backend: in fase 1 non c'e' login, quindi senza {@code SAM_SERVER_ADDRESS} il server
 * accetta connessioni solo da 127.0.0.1 (nel container Docker la variabile vale 0.0.0.0); vuota, il backend non parte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IndirizzoAscoltoTest {

    @Autowired
    WebServerApplicationContext contesto;

    @Test
    void senzaSamServerAddressIlServerAscoltaSoloSuLoopback() throws IOException {
        assumeTrue(System.getenv("SAM_SERVER_ADDRESS") == null && System.getProperty("SAM_SERVER_ADDRESS") == null,
                "SAM_SERVER_ADDRESS impostata nell'ambiente");
        var server = (TomcatWebServer) contesto.getWebServer();

        var indirizzo = (InetAddress) server.getTomcat().getConnector().getProperty("address");
        assertThat(indirizzo).isNotNull();
        assertThat(indirizzo.getHostAddress()).isEqualTo("127.0.0.1");
        try (var connessione = new Socket("127.0.0.1", server.getPort())) {
            assertThat(connessione.isConnected()).isTrue();
        }
    }

    @Test
    void samServerAddressCambiaLInterfaccia() throws IOException {
        assertThat(indirizzoConfigurato(Map.of(), "")).isEqualTo("127.0.0.1");
        assertThat(indirizzoConfigurato(Map.of("SAM_SERVER_ADDRESS", "0.0.0.0"), "")).isEqualTo("0.0.0.0");
        assertThat(indirizzoConfigurato(Map.of(), "SAM_SERVER_ADDRESS=0.0.0.0\n")).isEqualTo("0.0.0.0");
    }

    @Test
    void unaRigaVuotaNelFileEnvLasciaIlDefault() throws IOException {
        assertThat(indirizzoConfigurato(Map.of(), "SAM_SERVER_ADDRESS=\n")).isEqualTo("127.0.0.1");
        assertThat(indirizzoConfigurato(Map.of(), "SAM_SERVER_ADDRESS=''\n")).isEqualTo("127.0.0.1");
    }

    @Test
    void conSamServerAddressVuotaIlBackendNonParte() {
        var avvio = new SpringApplicationBuilder(SalaryManagerApplication.class)
                .profiles("test")
                .properties("server.port=0");

        assertThatThrownBy(() -> avvio.run("--SAM_SERVER_ADDRESS=").close())
                .rootCause()
                .hasMessageContaining("server.address (SAM_SERVER_ADDRESS) e' vuoto");
    }

    /**
     * {@code server.address} da application.yml con le sole variabili d'ambiente indicate e il contenuto di un file
     * {@code .env}.
     */
    private static String indirizzoConfigurato(Map<String, Object> variabili, String fileEnv) throws IOException {
        var ambiente = new StandardEnvironment();
        var sorgenti = ambiente.getPropertySources();
        sorgenti.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sorgenti.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variabili));
        new CaricatoreFileEnv().load("Config resource 'file [../.env]' via location 'optional:file:../.env[.env]'",
                new ByteArrayResource(fileEnv.getBytes(StandardCharsets.UTF_8))).forEach(sorgenti::addLast);
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sorgenti::addLast);
        return ambiente.getProperty("server.address");
    }
}
