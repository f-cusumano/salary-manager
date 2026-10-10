package com.fcusumano.sam.cedolini.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.config.LettoreProperties;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Errori gravi durante l'elaborazione (es. memoria esaurita aprendo un PDF costruito ad arte): il caricamento deve
 * arrivare comunque in uno stato terminale, mai restare IN_LETTURA.
 */
class ElaborazioneCaricamentiTest {

    private final CaricamentiRepository caricamenti = mock(CaricamentiRepository.class);
    private final CedoliniRepository cedolini = mock(CedoliniRepository.class);
    private final LettoreCedolini lettoreCedolini = mock(LettoreCedolini.class);
    private final ConsultazioneAnagrafica consultazione = mock(ConsultazioneAnagrafica.class);
    private ElaborazioneCaricamenti elaborazione;

    private ElaborazioneCaricamenti elaborazione(LettorePdf lettore) {
        when(cedolini.perHash(any(), any())).thenReturn(Optional.empty());
        var properties = new LettoreProperties(LettoreProperties.Lettore.FIXTURE, 1, "classpath:fixtures/cedolini/");
        elaborazione = new ElaborazioneCaricamenti(caricamenti, cedolini, lettore, lettoreCedolini, null, consultazione,
                properties);
        return elaborazione;
    }

    @AfterEach
    void chiudi() throws InterruptedException {
        if (elaborazione != null) {
            elaborazione.destroy();
        }
    }

    private static Lavoro lavoro(long id) {
        return new Lavoro(WorkspaceId.DEFAULT, new CaricamentoId(id), "bomba.pdf", new byte[] {1}, "hash-" + id, null);
    }

    @Test
    void memoriaEsauritaPortaIlCaricamentoInErrore() {
        elaborazione((contenuto, password, codici) -> {
            throw new OutOfMemoryError("Java heap space");
        }).accoda(List.of(lavoro(1)));

        verify(caricamenti, timeout(5000)).aggiorna(eq(WorkspaceId.DEFAULT), eq(new CaricamentoId(1)),
                eq(StatoCaricamento.ERRORE), eq(MessaggiCaricamento.ERRORE_IMPREVISTO), isNull(), isNull());
    }

    @Test
    void unAnnidamentoTroppoProfondoPortaIlCaricamentoInErrore() {
        elaborazione((contenuto, password, codici) -> {
            throw new StackOverflowError();
        }).accoda(List.of(lavoro(2)));

        verify(caricamenti, timeout(5000)).aggiorna(eq(WorkspaceId.DEFAULT), eq(new CaricamentoId(2)),
                eq(StatoCaricamento.ERRORE), eq(MessaggiCaricamento.ERRORE_IMPREVISTO), isNull(), isNull());
    }
}
