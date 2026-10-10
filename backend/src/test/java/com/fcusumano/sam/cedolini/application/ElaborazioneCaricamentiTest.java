package com.fcusumano.sam.cedolini.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CedolinoNonAcquisibileException.Dato;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf.DocumentoPdf;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import com.fcusumano.sam.cedolini.config.LettoreProperties;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Esiti conclusivi dell'elaborazione con il motivo dell'errore e la sorte del PDF originale e dell'associazione del
 * file (repository simulati, transazioni simulate): un caricamento arriva sempre in uno stato terminale, mai resta
 * IN_LETTURA, anche per errori gravi (es. memoria esaurita aprendo un PDF costruito ad arte) o se la transazione di
 * chiusura fallisce; il permesso di lettura si rilascia prima della chiusura. Dati inventati.
 */
class ElaborazioneCaricamentiTest {

    private static final WorkspaceId WS = WorkspaceId.DEFAULT;
    private static final long ATTESA_MS = 5000;

    private final CaricamentiRepository caricamenti = mock(CaricamentiRepository.class);
    private final CedoliniRepository cedolini = mock(CedoliniRepository.class);
    private final LettoreCedolini lettoreCedolini = mock(LettoreCedolini.class);
    private final AcquisizioneCedolino acquisizione = mock(AcquisizioneCedolino.class);
    private final ConsultazioneAnagrafica consultazione = mock(ConsultazioneAnagrafica.class);
    private final ApplicationEventPublisher eventi = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transazioni = mock(PlatformTransactionManager.class);
    private ElaborazioneCaricamenti elaborazione;

    @BeforeEach
    void simulazioni() {
        when(transazioni.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(cedolini.perHash(any(), any())).thenReturn(Optional.empty());
        when(caricamenti.eliminaAttribuzione(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void chiudi() throws InterruptedException {
        if (elaborazione != null) {
            elaborazione.destroy();
        }
    }

    private ElaborazioneCaricamenti elaborazione(LettorePdf lettore) {
        return elaborazione(lettore, 1);
    }

    private ElaborazioneCaricamenti elaborazione(LettorePdf lettore, int lettureContemporanee) {
        var properties = new LettoreProperties(LettoreProperties.Lettore.FIXTURE, lettureContemporanee,
                "classpath:fixtures/cedolini/");
        var chiusura = new ChiusuraCaricamento(new ScrittureSerializzate(transazioni), caricamenti, cedolini, eventi);
        elaborazione = new ElaborazioneCaricamenti(caricamenti, cedolini, lettore, lettoreCedolini, acquisizione,
                chiusura, consultazione, properties);
        return elaborazione;
    }

    /** Lettore PDF che apre qualunque contenuto senza protezione. */
    private static DocumentoPdf apri(byte[] contenuto, String password, Object codici) {
        return new DocumentoPdf(contenuto, 1, false);
    }

    private static Lavoro lavoro(long id) {
        return new Lavoro(WS, new CaricamentoId(id), "file-" + id + ".pdf", new byte[] {1, (byte) id}, "hash-" + id,
                null);
    }

    /** Attende il commit della transazione di chiusura (l'unica transazione: l'acquisizione e' simulata). */
    private void attendiChiusura() {
        verify(transazioni, timeout(ATTESA_MS)).commit(any());
    }

    // ------------------------------------------------------------------------------------------ errori di lettura

    @Test
    void unaScansioneSenzaTestoVaInErroreEConservaIlPdf() {
        var lavoro = lavoro(1);
        var errore = new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO, "Il PDF non contiene testo selezionabile");
        when(lettoreCedolini.estrai(any(), anyString())).thenThrow(errore);

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.PDF_SENZA_TESTO,
                errore.getMessage());
        verify(caricamenti).salvaPdfSeAssente(WS, lavoro.hash(), lavoro.contenuto());
        verify(caricamenti, never()).eliminaPdf(any(), any());
        verify(caricamenti, never()).eliminaAttribuzione(any(), any());
    }

    @Test
    void unPdfProtettoSenzaPasswordConservaLOriginaleCifrato() {
        var lavoro = lavoro(2);

        elaborazione((contenuto, password, codici) -> {
            throw new PdfNonLeggibileException(Causa.PROTETTO_DA_PASSWORD, PdfNonLeggibileException.MESSAGGIO_PASSWORD);
        }).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.PDF_PROTETTO,
                PdfNonLeggibileException.MESSAGGIO_PASSWORD);
        verify(caricamenti).salvaPdfSeAssente(WS, lavoro.hash(), lavoro.contenuto());
    }

    static Stream<Arguments> erroriNonCompletabili() {
        return Stream.of(
                Arguments.of(new PdfNonLeggibileException(Causa.NON_PDF, "Il file non è un PDF"),
                        MotivoErrore.PDF_NON_VALIDO),
                Arguments.of(new PdfNonLeggibileException(Causa.DANNEGGIATO, "Il PDF è danneggiato o non leggibile"),
                        MotivoErrore.PDF_NON_VALIDO),
                Arguments.of(new PdfNonLeggibileException(Causa.TROPPE_PAGINE, "Il PDF ha 120 pagine"),
                        MotivoErrore.PDF_TROPPE_PAGINE),
                Arguments.of(new EstrazioneCedolinoException(Motivo.PIU_CEDOLINI, "Il PDF contiene 2 cedolini distinti"),
                        MotivoErrore.PIU_CEDOLINI));
    }

    @ParameterizedTest
    @MethodSource("erroriNonCompletabili")
    void unFileNonCompletabileNonConservaIlPdf(EstrazioneCedolinoException errore, MotivoErrore motivo) {
        var lavoro = lavoro(3);
        when(lettoreCedolini.estrai(any(), anyString())).thenThrow(errore);

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), motivo, errore.getMessage());
        verify(caricamenti).eliminaPdf(WS, lavoro.hash());
        verify(caricamenti, never()).salvaPdfSeAssente(any(), any(), any());
    }

    @Test
    void unFileArchiviatoNelFrattempoNonConservaNeEliminaIlPdf() {
        var lavoro = lavoro(4);
        when(cedolini.perHash(WS, lavoro.hash())).thenReturn(Optional.empty(), Optional.of(new CedolinoId(40)));
        when(lettoreCedolini.estrai(any(), anyString()))
                .thenThrow(new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio non disponibile"));

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(eq(WS), eq(lavoro.caricamentoId()),
                eq(MotivoErrore.SERVIZIO_NON_DISPONIBILE), anyString());
        verify(caricamenti, never()).salvaPdfSeAssente(any(), any(), any());
        verify(caricamenti, never()).eliminaPdf(any(), any());
    }

    // ------------------------------------------------------------------------------------------ acquisizione

    @Test
    void datiIndispensabiliMancantiVannoInErroreEConservanoIlPdf() {
        var lavoro = lavoro(5);
        when(lettoreCedolini.estrai(any(), anyString())).thenReturn(new RisultatoEstrazione(null, "{}", "prova"));
        when(acquisizione.acquisisci(any(), any())).thenThrow(
                new CedolinoNonAcquisibileException(Dato.PERIODO, MessaggiCaricamento.PERIODO_NON_LEGGIBILE));

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.DATI_INCOMPLETI,
                MessaggiCaricamento.nonAcquisibile(MessaggiCaricamento.PERIODO_NON_LEGGIBILE));
        verify(caricamenti).salvaPdfSeAssente(WS, lavoro.hash(), lavoro.contenuto());
    }

    @Test
    void unCedolinoScartatoEliminaPdfEAssociazioneDelFile() {
        var lavoro = lavoro(6);
        var esistente = new CedolinoId(60);
        var messaggio = "Cedolino aggiuntivo di Marzo 2024: non gestito";
        when(lettoreCedolini.estrai(any(), anyString())).thenReturn(new RisultatoEstrazione(null, "{}", "prova"));
        when(acquisizione.acquisisci(any(), any()))
                .thenReturn(new Scartato(StatoCaricamento.AGGIUNTIVO_NON_GESTITO, messaggio, esistente));

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).aggiorna(WS, lavoro.caricamentoId(), StatoCaricamento.AGGIUNTIVO_NON_GESTITO, messaggio,
                null, esistente);
        verify(caricamenti).eliminaPdf(WS, lavoro.hash());
        verify(caricamenti).eliminaAttribuzione(WS, lavoro.hash());
        verify(eventi, never()).publishEvent(any(Object.class));
    }

    @Test
    void unFileGiaArchiviatoEDuplicatoERicalcolaIlRapportoAssociato() {
        var lavoro = lavoro(7);
        var esistente = new CedolinoId(70);
        var rapporto = new RapportoLavoroId(7);
        when(cedolini.perHash(WS, lavoro.hash())).thenReturn(Optional.of(esistente));
        when(caricamenti.eliminaAttribuzione(WS, lavoro.hash())).thenReturn(Optional.of(rapporto));

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).aggiorna(WS, lavoro.caricamentoId(), StatoCaricamento.DUPLICATO,
                MessaggiCaricamento.FILE_GIA_CARICATO, null, esistente);
        verify(caricamenti).eliminaPdf(WS, lavoro.hash());
        verify(eventi).publishEvent(new CaricamentiInErroreModificati(Set.of(rapporto)));
        verify(lettoreCedolini, never()).estrai(any(), anyString());
    }

    // ------------------------------------------------------------------------------------------ errori gravi

    @Test
    void memoriaEsauritaPortaIlCaricamentoInErrore() {
        var lavoro = lavoro(8);

        elaborazione((contenuto, password, codici) -> {
            throw new OutOfMemoryError("Java heap space");
        }).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.ERRORE_IMPREVISTO,
                MessaggiCaricamento.ERRORE_IMPREVISTO);
        verify(caricamenti).salvaPdfSeAssente(WS, lavoro.hash(), lavoro.contenuto());
    }

    @Test
    void unAnnidamentoTroppoProfondoPortaIlCaricamentoInErrore() {
        var lavoro = lavoro(9);

        elaborazione((contenuto, password, codici) -> {
            throw new StackOverflowError();
        }).accoda(List.of(lavoro));

        attendiChiusura();
        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.ERRORE_IMPREVISTO,
                MessaggiCaricamento.ERRORE_IMPREVISTO);
    }

    @Test
    void unLavoroRifiutatoDopoLaChiusuraEInterrottoEConservaIlPdf() throws InterruptedException {
        var lavoro = lavoro(10);
        elaborazione(ElaborazioneCaricamentiTest::apri).destroy();

        elaborazione.accoda(List.of(lavoro));

        verify(caricamenti).registraErrore(WS, lavoro.caricamentoId(), MotivoErrore.INTERROTTO,
                MessaggiCaricamento.INTERROTTO);
        verify(caricamenti).salvaPdfSeAssente(WS, lavoro.hash(), lavoro.contenuto());
    }

    // ------------------------------------------------------------------------------------------ chiusura

    @Test
    void seLaTransazioneDiChiusuraFallisceIlCaricamentoVaComunqueInErrore() {
        var lavoro = lavoro(11);
        var errore = new EstrazioneCedolinoException(Motivo.LAYOUT_NON_RICONOSCIUTO, "Layout non riconosciuto");
        when(lettoreCedolini.estrai(any(), anyString())).thenThrow(errore);
        doThrow(new IllegalStateException("database non disponibile"))
                .when(caricamenti).salvaPdfSeAssente(any(), any(), any());

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        // la transazione annullata ha perso anche lo stato: il ripiego lo registra di nuovo, senza transazione
        verify(caricamenti, timeout(ATTESA_MS).times(2)).registraErrore(WS, lavoro.caricamentoId(),
                MotivoErrore.LAYOUT_NON_RICONOSCIUTO, errore.getMessage());
        verify(transazioni).rollback(any());
        verify(transazioni, never()).commit(any());
    }

    @Test
    void seLaTransazioneDiUnoScartoFallisceLoStatoVieneComunqueRegistrato() {
        var lavoro = lavoro(12);
        var esistente = new CedolinoId(120);
        when(cedolini.perHash(WS, lavoro.hash())).thenReturn(Optional.of(esistente));
        doThrow(new IllegalStateException("database non disponibile")).when(caricamenti).eliminaPdf(any(), any());

        elaborazione(ElaborazioneCaricamentiTest::apri).accoda(List.of(lavoro));

        verify(caricamenti, timeout(ATTESA_MS).times(2)).aggiorna(WS, lavoro.caricamentoId(),
                StatoCaricamento.DUPLICATO, MessaggiCaricamento.FILE_GIA_CARICATO, null, esistente);
        verify(transazioni).rollback(any());
    }

    @Test
    void ilPermessoDiLetturaSiRilasciaPrimaDellaChiusura() throws InterruptedException {
        var primo = lavoro(13);
        var secondo = lavoro(14);
        when(lettoreCedolini.estrai(any(), anyString()))
                .thenThrow(new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO, "Scansione"));
        var sblocco = new CountDownLatch(1);
        var bloccata = new AtomicBoolean();
        doAnswer(invocazione -> {
            if (bloccata.compareAndSet(false, true)) {
                // la prima chiusura resta in attesa (es. lock del workspace occupato da un'altra scrittura)
                sblocco.await(ATTESA_MS * 2, TimeUnit.MILLISECONDS);
            }
            return null;
        }).when(caricamenti).registraErrore(any(), any(), any(), any());

        // una sola lettura alla volta: il secondo file si legge solo se la prima chiusura non tiene il permesso
        elaborazione(ElaborazioneCaricamentiTest::apri, 1).accoda(List.of(primo, secondo));

        verify(lettoreCedolini, timeout(ATTESA_MS).times(2)).estrai(any(), anyString());
        assertThat(sblocco.getCount()).as("prima chiusura ancora in attesa").isEqualTo(1);
        sblocco.countDown();
        verify(caricamenti, timeout(ATTESA_MS).times(2)).salvaPdfSeAssente(eq(WS), anyString(), any());
        verify(caricamenti, never()).aggiorna(any(), any(), eq(StatoCaricamento.ERRORE), any(), isNull(), isNull());
    }
}
