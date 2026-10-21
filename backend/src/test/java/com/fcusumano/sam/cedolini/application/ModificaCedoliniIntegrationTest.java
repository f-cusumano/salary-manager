package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.CF_GIOVANNA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conContratto;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDipendente;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conPeriodo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conScelte;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conTotali;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.n;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.ModificaCedolini;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.EventiRegistrati;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.LettoreProgrammabile;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Modifica a mano dei cedolini archiviati sul database H2 (schema Liquibase): dati attuali per il modulo, verifica
 * senza scritture, sostituzione del cedolino con lo stesso PDF, ricostruzione del rapporto e dei dati derivati,
 * spostamento a un altro rapporto, conflitti. Dati inventati (fixture "rossi-alfa").
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SupportoCaricamenti.Configurazione.class)
class ModificaCedoliniIntegrationTest {

    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);
    private static final CedolinoManuale.Dipendente GIOVANNA =
            new CedolinoManuale.Dipendente(CF_GIOVANNA, "Giovanna", "Bianchi", null, null);

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    LettoreProgrammabile lettoreCedolini;
    @Autowired
    EventiRegistrati eventi;
    @Autowired
    DSLContext dsl;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ModificaCedolini modifica;
    @Autowired
    ConsultazioneAnagrafica anagrafica;

    @BeforeEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento_attribuzione");
        dsl.execute("DELETE FROM caricamento_pdf");
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM password_pdf");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
        lettoreCedolini.azzera();
        eventi.azzera();
    }

    @Test
    void iDatiDelModuloSonoQuelliDaCuiEStatoArchiviatoIlCedolino() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var rapporto = rapporto(aprile.rapportoId());

        var dati = modifica.dati(aprile.cedolinoId());

        assertThat(dati.personaId()).isEqualTo(rapporto.personaId().valore());
        assertThat(dati.aziendaId()).isEqualTo(rapporto.aziendaId().valore());
        assertThat(dati.dipendente().codiceFiscale()).isEqualTo("RSSMRA80A01H501U");
        assertThat(dati.dipendente().codiceDipendente()).isEqualTo("000123");
        assertThat(dati.datoreLavoro().posizioneInail()).isEqualTo("98765432/10");
        assertThat(dati.datiContrattuali().ccnl()).isEqualTo("CCNL Metalmeccanica Industria");
        assertThat(dati.datiContrattuali().codiceCnel()).isNull();
        assertThat(dati.datiContrattuali().dataAssunzione()).isEqualTo(LocalDate.of(2021, 9, 1));
        assertThat(dati.periodo()).isEqualTo(new CedolinoManuale.Periodo(2023, 4, TipoMensilita.ORDINARIA,
                LocalDate.of(2023, 4, 27), java.time.YearMonth.of(2023, 3)));
        assertThat(dati.totali().netto()).isEqualByComparingTo("1699.00");
        assertThat(dati.totali().arrotondamento()).isEqualByComparingTo("-0.40");
        assertThat(dati.retribuzione().superminimoAssorbibile()).isTrue();
        assertThat(dati.voci()).isNotEmpty().allSatisfy(v -> assertThat(v.categoria()).isNotNull());
    }

    @Test
    void salvareIDatiInvariatiSostituisceIlCedolinoConLoStessoPdf() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var pdfPrima = archivio.pdf(aprile.cedolinoId()).orElseThrow().contenuto();
        var hashPrima = hashDi(aprile.cedolinoId());
        eventi.azzera();

        var modificato = modifica.modifica(aprile.cedolinoId(), modifica.dati(aprile.cedolinoId()));

        assertThat(modificato.cedolinoId()).isNotEqualTo(aprile.cedolinoId());
        assertThat(modificato.rapportoId()).isEqualTo(aprile.rapportoId());
        assertThat(modificato.competenza()).isEqualTo(new Competenza.Mensilita(2023, 4));
        assertThat(modificato.rapportoEliminato()).isFalse();
        assertThat(archivio.dettaglio(aprile.cedolinoId())).isEmpty();
        assertThat(archivio.pdf(modificato.cedolinoId()).orElseThrow().contenuto()).isEqualTo(pdfPrima);
        assertThat(hashDi(modificato.cedolinoId())).isEqualTo(hashPrima);
        assertThat(archivio.dettaglio(modificato.cedolinoId())).hasValueSatisfying(d -> {
            assertThat(d.modificatoAMano()).isTrue();
            assertThat(d.inseritoAMano()).isFalse();
            assertThat(d.confidenza()).isNull();
            assertThat(d.dati().netto()).isEqualByComparingTo("1699.00");
            assertThat(d.nomeFile()).isEqualTo(nomeFileDi(modificato.cedolinoId()));
        });
        assertThat(dsl.fetchSingle("SELECT modello_estrazione FROM cedolino WHERE id = ?",
                modificato.cedolinoId().valore()).get(0, String.class))
                .isEqualTo(ModelliEstrazione.MODELLO_MODIFICA_MANUALE);
        assertThat(caricamento.stato(List.of(aprile.id())).getFirst().cedolinoId())
                .as("il caricamento che lo aveva archiviato punta al cedolino modificato")
                .isEqualTo(modificato.cedolinoId());
        assertThat(eventi.acquisiti).containsExactly(new CedolinoAcquisito(modificato.cedolinoId(),
                aprile.rapportoId(), new Competenza.Mensilita(2023, 4)));
        assertThat(eventi.eliminati).isEmpty();
        assertThat(archivio.sintesiDiRapporto(aprile.rapportoId())).hasSize(1);

        var ricaricato = carica(new FileCaricato("di-nuovo.pdf", pdfFixture("rossi-alfa-2023-04")));
        assertThat(ricaricato.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(ricaricato.cedolinoEsistenteId()).isEqualTo(modificato.cedolinoId());
    }

    @Test
    void iDatiModificatiSostituisconoQuelliLettiEIDatiDerivatiSonoRicalcolati() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var dati = modifica.dati(aprile.cedolinoId());
        var corretti = conTotali(dati, new CedolinoManuale.Totali(dati.totali().lordo(),
                dati.totali().totaleCompetenze(), dati.totali().totaleTrattenute(), n("-0.40"), n("1699.40")));

        var verifica = modifica.verifica(aprile.cedolinoId(), corretti);
        assertThat(verifica.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(verifica.nuovaPersona()).isFalse();
        assertThat(verifica.nuovoRapporto()).isFalse();
        assertThat(verifica.rapportoId()).isEqualTo(aprile.rapportoId());
        assertThat(verifica.avvisi()).doesNotContain(ModificaCedoliniService.AVVISO_ALTRO_RAPPORTO);
        assertThat(archivio.dettaglio(aprile.cedolinoId())).as("la verifica non scrive nulla").isPresent();

        var modificato = modifica.modifica(aprile.cedolinoId(), corretti);

        assertThat(archivio.dettaglio(modificato.cedolinoId()).orElseThrow().dati().netto())
                .isEqualByComparingTo("1699.40");
        assertThat(modifica.dati(modificato.cedolinoId()).totali().netto()).isEqualByComparingTo("1699.40");
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rapporto").where("rapporto_id = ?",
                aprile.rapportoId().valore()))).isEqualTo(1);
    }

    @Test
    void ilRapportoSiRicostruisceDalCedolinoModificato() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var conCessazione = conContratto(modifica.dati(aprile.cedolinoId()), new CedolinoManuale.DatiContrattuali(
                LocalDate.of(2021, 9, 1), LocalDate.of(2023, 4, 30), "Impiegato", "5",
                "CCNL Metalmeccanica Industria", null));
        var primo = modifica.modifica(aprile.cedolinoId(), conCessazione);
        assertThat(rapporto(primo.rapportoId()).dataCessazione()).isEqualTo(LocalDate.of(2023, 4, 30));

        // la cessazione era sbagliata: tolta dal cedolino, non resta sul rapporto
        var senzaCessazione = conContratto(modifica.dati(primo.cedolinoId()), new CedolinoManuale.DatiContrattuali(
                LocalDate.of(2021, 9, 1), null, "Impiegato", "5", "CCNL Metalmeccanica Industria", null));
        var secondo = modifica.modifica(primo.cedolinoId(), senzaCessazione);

        assertThat(secondo.rapportoId()).isEqualTo(aprile.rapportoId());
        assertThat(rapporto(secondo.rapportoId()).dataCessazione()).isNull();
        assertThat(rapporto(secondo.rapportoId()).qualifica()).isEqualTo("Impiegato");
    }

    @Test
    void cambiandoIlPeriodoIlCedolinoPassaAUnaMensilitaLibera() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var marzo = caricaFixture("rossi-alfa-2023-03");
        var maggio = conPeriodo(modifica.dati(aprile.cedolinoId()),
                new CedolinoManuale.Periodo(2023, 5, TipoMensilita.ORDINARIA, null, null));

        var verifica = modifica.verifica(aprile.cedolinoId(), maggio);
        assertThat(verifica.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(verifica.competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
        assertThat(verifica.avvisi()).contains("Il cedolino passerà da Aprile 2023 a Maggio 2023");

        var modificato = modifica.modifica(aprile.cedolinoId(), maggio);

        assertThat(modificato.competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
        assertThat(archivio.sintesiDiRapporto(aprile.rapportoId()))
                .extracting(s -> s.competenza())
                .containsExactly(new Competenza.Mensilita(2023, 5), new Competenza.Mensilita(2023, 3));
        assertThat(archivio.dettaglio(marzo.cedolinoId())).isPresent();
    }

    @Test
    void unaMensilitaGiaPresenteNonSiModificaENullaCambia() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        caricaFixture("rossi-alfa-2023-03");
        var comeMarzo = conPeriodo(modifica.dati(aprile.cedolinoId()),
                new CedolinoManuale.Periodo(2023, 3, TipoMensilita.ORDINARIA, null, null));
        var prima = conteggi();

        var verifica = modifica.verifica(aprile.cedolinoId(), comeMarzo);
        assertThat(verifica.esito()).isEqualTo(Esito.AGGIUNTIVO_NON_GESTITO);
        assertThat(verifica.messaggio()).isNotBlank();

        assertThatThrownBy(() -> modifica.modifica(aprile.cedolinoId(), comeMarzo))
                .isInstanceOf(OperazioneNonConsentitaException.class);
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(archivio.dettaglio(aprile.cedolinoId())).as("stesso id: nulla e' cambiato").isPresent();
    }

    @Test
    void conUnaPersonaNuovaIlCedolinoCambiaRapportoEQuelloRimastoVuotoSiElimina() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var rapportoPrima = rapporto(aprile.rapportoId());
        var aGiovanna = conDipendente(conScelte(modifica.dati(aprile.cedolinoId()), null,
                rapportoPrima.aziendaId().valore()), GIOVANNA);

        var verifica = modifica.verifica(aprile.cedolinoId(), aGiovanna);
        assertThat(verifica.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(verifica.nuovaPersona()).isTrue();
        assertThat(verifica.nuovoRapporto()).isTrue();
        assertThat(verifica.avvisi()).contains(ModificaCedoliniService.AVVISO_ALTRO_RAPPORTO,
                ModificaCedoliniService.AVVISO_RAPPORTO_ELIMINATO);
        eventi.azzera();

        var modificato = modifica.modifica(aprile.cedolinoId(), aGiovanna);

        assertThat(modificato.rapportoId()).isNotEqualTo(aprile.rapportoId());
        assertThat(modificato.rapportoEliminato()).isTrue();
        assertThat(anagrafica.trovaRapporto(aprile.rapportoId())).isEmpty();
        assertThat(anagrafica.trovaPersona(rapportoPrima.personaId())).as("Mario Rossi senza rapporti").isEmpty();
        var nuovo = rapporto(modificato.rapportoId());
        assertThat(nuovo.aziendaId()).isEqualTo(rapportoPrima.aziendaId());
        assertThat(anagrafica.trovaPersona(nuovo.personaId()).orElseThrow().codiceFiscale()).isEqualTo(CF_GIOVANNA);
        assertThat(eventi.eliminati).containsExactly(new CedolinoEliminato(aprile.cedolinoId(), aprile.rapportoId(),
                new Competenza.Mensilita(2023, 4)));
    }

    @Test
    void conUnAltroCedolinoNelRapportoQuelloPrecedenteRestaERicalcolato() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var marzo = caricaFixture("rossi-alfa-2023-03");
        var rapportoPrima = rapporto(aprile.rapportoId());
        var aGiovanna = conDipendente(conScelte(modifica.dati(aprile.cedolinoId()), null,
                rapportoPrima.aziendaId().valore()), GIOVANNA);

        var modificato = modifica.modifica(aprile.cedolinoId(), aGiovanna);

        assertThat(modificato.rapportoEliminato()).isFalse();
        assertThat(archivio.sintesiDiRapporto(aprile.rapportoId())).extracting(s -> s.id())
                .containsExactly(marzo.cedolinoId());
        assertThat(archivio.sintesiDiRapporto(modificato.rapportoId())).extracting(s -> s.id())
                .containsExactly(modificato.cedolinoId());
    }

    @Test
    void datiNonValidiOCedolinoInesistenteNonModificanoNulla() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var senzaNetto = conTotali(modifica.dati(aprile.cedolinoId()),
                new CedolinoManuale.Totali(null, n("2348.00"), n("648.60"), null, null));
        var prima = conteggi();

        assertThatThrownBy(() -> modifica.modifica(aprile.cedolinoId(), senzaNetto))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .extracting(DatiNonValidiException.ErroreCampo::campo).contains("totali.netto"));
        assertThatThrownBy(() -> modifica.dati(new CedolinoId(999_999)))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThatThrownBy(() -> modifica.modifica(new CedolinoId(999_999), modifica.dati(aprile.cedolinoId())))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThat(conteggi()).isEqualTo(prima);
    }

    // ------------------------------------------------------------------------------------------ supporto

    private RapportoLavoro rapporto(RapportoLavoroId id) {
        return anagrafica.trovaRapporto(id).orElseThrow();
    }

    private String hashDi(CedolinoId id) {
        return dsl.fetchSingle("SELECT hash_contenuto FROM cedolino WHERE id = ?", id.valore()).get(0, String.class);
    }

    private String nomeFileDi(CedolinoId id) {
        return dsl.fetchSingle("SELECT nome_file FROM cedolino WHERE id = ?", id.valore()).get(0, String.class);
    }

    private Map<String, Integer> conteggi() {
        var conteggi = new LinkedHashMap<String, Integer>();
        for (var tabella : List.of("persona", "azienda", "rapporto_lavoro", "cedolino", "cedolino_pdf",
                "riepilogo_rapporto", "segnalazione", "caricamento")) {
            conteggi.put(tabella, dsl.fetchCount(dsl.selectFrom(tabella)));
        }
        return conteggi;
    }

    private Caricamento caricaFixture(String nome) {
        var esito = carica(new FileCaricato(nome + ".pdf", pdfFixture(nome)));
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        return esito;
    }

    private Caricamento carica(FileCaricato file) {
        return attendi(caricamento.avvia(List.of(file)).getFirst().lotto());
    }

    private Caricamento attendi(UUID lotto) {
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).getFirst().stato().terminale());
        return caricamento.statoLotto(lotto).getFirst();
    }
}
