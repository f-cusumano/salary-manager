package com.fcusumano.sam.segnalazioni.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fcusumano.sam.segnalazioni.domain.ContestoRapporto;
import com.fcusumano.sam.segnalazioni.domain.RegolaSegnalazione;
import com.fcusumano.sam.segnalazioni.domain.SegnalazioneRilevata;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Una regola in errore lascia nel log a livello ERROR solo il codice della regola e il tipo di errore. */
class SegnalazioniServiceTest {

    /** Messaggio con dati personali inventati, come potrebbe produrlo un'eccezione sollevata da una regola. */
    private static final String MESSAGGIO = "RSSMRA80A01H501U 1234,56";

    private final Logger logger = (Logger) LoggerFactory.getLogger(SegnalazioniService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level livelloPrecedente;

    @BeforeEach
    void collegaAppender() {
        livelloPrecedente = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void scollegaAppender() {
        logger.detachAppender(appender);
        logger.setLevel(livelloPrecedente);
    }

    @Test
    void ilLogDiErroreNonContieneIlMessaggioNeLoStackTrace() {
        SegnalazioniService.regolaInErrore(new RegolaInErrore(), new IllegalArgumentException(MESSAGGIO));

        var gravi = appender.list.stream().filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN)).toList();
        assertThat(gravi).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage())
                    .contains("PROVA_IN_ERRORE", IllegalArgumentException.class.getName())
                    .doesNotContain(MESSAGGIO);
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void ilDettaglioDellErroreSoloADebug() {
        logger.setLevel(Level.DEBUG);

        SegnalazioniService.regolaInErrore(new RegolaInErrore(), new IllegalArgumentException(MESSAGGIO));

        assertThat(appender.list).filteredOn(e -> e.getThrowableProxy() != null).singleElement()
                .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.DEBUG));
    }

    private static final class RegolaInErrore implements RegolaSegnalazione {

        @Override
        public String codice() {
            return "PROVA_IN_ERRORE";
        }

        @Override
        public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
            throw new IllegalArgumentException(MESSAGGIO);
        }
    }
}
