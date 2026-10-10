package com.fcusumano.sam.anagrafica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.CcnlRapportoModificato;
import com.fcusumano.sam.anagrafica.CensimentoAnagrafica;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.DatiPersona;
import com.fcusumano.sam.anagrafica.DatiRapporto;
import com.fcusumano.sam.anagrafica.FontePeriodo;
import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroEliminato;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anagrafica sul database H2 (schema Liquibase): censimento da cedolino, matching delle aziende tra layout diversi,
 * etichette anti-omonimia, periodi, CCNL ed eliminazione. Dati inventati.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@ActiveProfiles("test")
@Transactional
class AnagraficaServiceIntegrationTest {

    private static final String CF_MARIO = "RSSMRA80A01H501U";
    private static final String CF_MARIO_OMONIMO = "RSSMRA75C12F205K";
    private static final String CF_GIUSEPPINA = "VRDGPP85M41F205X";
    private static final String PIVA_ALFA = "01234567890";
    private static final String PIVA_ALFA_OMONIMA = "11223344556";
    private static final String PIVA_BETA = "09876543210";

    @Autowired
    CensimentoAnagrafica censimento;
    @Autowired
    ConsultazioneAnagrafica consultazione;
    @Autowired
    GestioneRapporti gestione;

    @Test
    void personaTrovataPerCodiceFiscaleNormalizzatoECompletataSenzaSovrascrivereIlNome() {
        var creata = censimento.trovaOCreaPersona(new DatiPersona(" rssmra80a01h501u", "MARIO", "ROSSI", null));
        var ritrovata = censimento.trovaOCreaPersona(
                new DatiPersona("RSSMRA 80A01 H501U", "Mario Antonio", "Rossi", LocalDate.of(1980, 1, 1)));

        assertThat(creata.codiceFiscale()).isEqualTo(CF_MARIO);
        assertThat(creata.nomeCompleto()).isEqualTo("Mario Rossi");
        assertThat(ritrovata.id()).isEqualTo(creata.id());
        assertThat(ritrovata.nome()).isEqualTo("Mario");
        assertThat(consultazione.trovaPersona(creata.id())).get()
                .extracting(Persona::dataNascita).isEqualTo(LocalDate.of(1980, 1, 1));
    }

    @Test
    void codiceFiscaleFormalmenteNonValidoAccettatoComunque() {
        var persona = censimento.trovaOCreaPersona(new DatiPersona("RSSMRA80A01H501A", "Mario", "Rossi", null));
        assertThat(persona.codiceFiscale()).isEqualTo("RSSMRA80A01H501A");
    }

    @Test
    void codiceFiscaleMancanteOTroppoLungoRifiutato() {
        assertThatThrownBy(() -> censimento.trovaOCreaPersona(new DatiPersona("  ", "Mario", "Rossi", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> censimento.trovaOCreaPersona(
                new DatiPersona(CF_MARIO + "X", "Mario", "Rossi", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> censimento.trovaOCreaPersona(new DatiPersona(CF_MARIO, " ", "Rossi", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elencoOrdinatoPerCognomeConCodiceFiscaleSoloPerGliOmonimi() {
        censimento.trovaOCreaPersona(new DatiPersona(CF_GIUSEPPINA, "Giuseppina", "Verdi", null));
        censimento.trovaOCreaPersona(new DatiPersona(CF_MARIO, "Mario", "Rossi", null));
        censimento.trovaOCreaPersona(new DatiPersona(CF_MARIO_OMONIMO, "MARIO", "ROSSI", null));

        assertThat(consultazione.elencoPersone()).extracting(PersonaSintesi::etichetta).containsExactly(
                "Mario Rossi (" + CF_MARIO + ")",
                "Mario Rossi (" + CF_MARIO_OMONIMO + ")",
                "Giuseppina Verdi");
    }

    @Test
    void stessaAziendaConCodiceFiscaleAssenteInUnLayoutEPresenteInUnAltro() {
        // layout senza CF/P.IVA, con INPS e INAIL concatenati
        var primo = azienda(new DatiAzienda(null, null, "1234567890 12345678/90", "ALFA SOFTWARE S.R.L.",
                "Via Roma 1, Milano", null, YearMonth.of(2024, 1)));
        // layout con CF numerico e matricola INPS pulita
        var secondo = azienda(new DatiAzienda(PIVA_ALFA, null, "1234567890", "Alfa Software S.r.l.",
                "Via Verdi 2, Milano", "0001", YearMonth.of(2024, 2)));
        // layout con sola P.IVA (incrociata con il CF numerico) e ragione sociale scritta diversamente
        var terzo = azienda(new DatiAzienda(null, "IT" + PIVA_ALFA, null, "Alfa Software - SRL", null, null,
                YearMonth.of(2023, 12)));

        assertThat(secondo.id()).isEqualTo(primo.id());
        assertThat(terzo.id()).isEqualTo(primo.id());
        assertThat(terzo.codiceFiscale()).isEqualTo(PIVA_ALFA);
        assertThat(terzo.partitaIva()).isEqualTo(PIVA_ALFA);
        assertThat(terzo.posizioneInps()).isEqualTo("1234567890");
        // gli attributi restano quelli del cedolino piu' recente (febbraio), non del terzo (dicembre)
        assertThat(terzo.ragioneSociale()).isEqualTo("Alfa Software S.r.l.");
        assertThat(terzo.indirizzo()).isEqualTo("Via Verdi 2, Milano");
        assertThat(terzo.codiceAzienda()).isEqualTo("0001");
    }

    @Test
    void inpsConcatenatoAInailRiconosceLaStessaAzienda() {
        var conInps = azienda(new DatiAzienda(null, PIVA_BETA, "1234567890", "Beta Consulting S.p.A.", null, null));
        var concatenato = azienda(new DatiAzienda(null, null, "123456789012345678", "BETA CONSULTING", null, null));

        assertThat(concatenato.id()).isEqualTo(conInps.id());
    }

    @Test
    void ragioneSocialeConFormeSocietarieDiverse() {
        var a = azienda(new DatiAzienda(null, null, null, "Beta Consulting S.p.A.", null, null));
        var b = azienda(new DatiAzienda(null, null, null, "BETA CONSULTING SPA", null, null));
        var c = azienda(new DatiAzienda(null, null, null, "Beta Consulting Società per Azioni", null, null));

        assertThat(b.id()).isEqualTo(a.id());
        assertThat(c.id()).isEqualTo(a.id());
    }

    @Test
    void omonimeConPartiteIvaDiverseRestanoDistinte() {
        var alfa = azienda(new DatiAzienda(null, PIVA_ALFA, null, "Alfa Software S.r.l.", null, null));
        var omonima = azienda(new DatiAzienda(null, PIVA_ALFA_OMONIMA, null, "ALFA SOFTWARE SRL", null, null));

        assertThat(omonima.id()).isNotEqualTo(alfa.id());
        assertThat(consultazione.trovaAzienda(omonima.id())).get()
                .extracting(Azienda::partitaIva).isEqualTo(PIVA_ALFA_OMONIMA);
    }

    @Test
    void ragioneSocialeMancanteRifiutata() {
        assertThatThrownBy(() -> censimento.trovaOCreaAzienda(new DatiAzienda(PIVA_ALFA, null, null, " ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Azienda azienda(DatiAzienda dati) {
        return censimento.trovaOCreaAzienda(dati);
    }

    @Test
    void rapportoCreatoEAggiornatoSoloDaCedoliniPiuRecenti() {
        var mario = mario();
        var alfa = alfa();
        var creato = censimento.trovaOCreaRapporto(mario.id(), alfa.id(), new DatiRapporto(
                LocalDate.of(2020, 3, 16), null, "Impiegato", "4", "0042", "COMMERCIO", YearMonth.of(2024, 3)));
        var dopoVecchio = censimento.trovaOCreaRapporto(mario.id(), alfa.id(), new DatiRapporto(
                null, null, "Apprendista", "5", null, null, YearMonth.of(2023, 1)));
        var dopoRecente = censimento.trovaOCreaRapporto(mario.id(), alfa.id(), new DatiRapporto(
                null, null, "Quadro", "Q", null, null, YearMonth.of(2024, 6)));

        assertThat(dopoVecchio.id()).isEqualTo(creato.id());
        assertThat(dopoVecchio.qualifica()).isEqualTo("Impiegato");
        assertThat(dopoRecente.qualifica()).isEqualTo("Quadro");
        assertThat(dopoRecente.livello()).isEqualTo("Q");
        assertThat(dopoRecente.dataAssunzione()).isEqualTo(LocalDate.of(2020, 3, 16));
        assertThat(dopoRecente.ccnlCodice()).isEqualTo("COMMERCIO");
        assertThat(dopoRecente.ccnlSceltoDaUtente()).isFalse();
    }

    @Test
    void ccnlRiconosciutoInesistenteIgnorato() {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                null, null, null, null, null, "NON_ESISTE", YearMonth.of(2024, 1)));
        assertThat(rapporto.ccnlDaIndicare()).isTrue();
    }

    @Test
    void personaOAziendaInesistentiRifiutate() {
        var alfa = alfa();
        var mario = mario();
        var dati = new DatiRapporto(null, null, null, null, null, null, YearMonth.of(2024, 1));
        assertThatThrownBy(() -> censimento.trovaOCreaRapporto(
                new PersonaId(999_999), alfa.id(), dati))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThatThrownBy(() -> censimento.trovaOCreaRapporto(
                mario.id(), new AziendaId(999_999), dati))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    @Test
    void sovrascriviDatiRapportoForzaIValori() {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                LocalDate.of(2020, 3, 16), LocalDate.of(2024, 6, 30), "Quadro", "Q", "0042", "COMMERCIO",
                YearMonth.of(2024, 6)));

        var forzato = censimento.sovrascriviDatiRapporto(rapporto.id(), new DatiRapporto(
                LocalDate.of(2020, 3, 16), null, "Impiegato", "4", null, null, YearMonth.of(2024, 5)));

        assertThat(forzato.dataCessazione()).isNull();
        assertThat(forzato.qualifica()).isEqualTo("Impiegato");
        assertThat(forzato.codiceDipendente()).isNull();
        assertThat(forzato.ccnlDaIndicare()).isTrue();
        // dopo la forzatura vale di nuovo il criterio di recenza dal nuovo riferimento (maggio)
        var aggiornato = censimento.trovaOCreaRapporto(rapporto.personaId(), rapporto.aziendaId(),
                new DatiRapporto(null, null, "Apprendista", null, null, null, YearMonth.of(2024, 4)));
        assertThat(aggiornato.qualifica()).isEqualTo("Impiegato");
    }

    @Test
    void periodiEOrdineCronologicoInversoConAttualeInAlto() {
        var mario = mario();
        var alfa = alfa();
        var beta = censimento.trovaOCreaAzienda(new DatiAzienda(null, PIVA_BETA, null, "Beta Consulting S.p.A.",
                null, null));
        var passato = rapporto(mario, alfa, LocalDate.of(2018, 4, 2), null, YearMonth.of(2018, 4),
                YearMonth.of(2023, 12));
        var attuale = rapporto(mario, beta, null, null, YearMonth.of(2024, 1), YearMonth.of(2026, 2));

        var rapporti = consultazione.rapportiDiPersona(mario.id());

        assertThat(rapporti).extracting(RapportoSintesi::id).containsExactly(attuale.id(), passato.id());
        var periodoAttuale = rapporti.getFirst().periodo();
        assertThat(periodoAttuale.attuale()).isTrue();
        assertThat(periodoAttuale.da()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(periodoAttuale.a()).isNull();
        assertThat(periodoAttuale.fonteInizio()).isEqualTo(FontePeriodo.CEDOLINI);
        var periodoPassato = rapporti.getLast().periodo();
        assertThat(periodoPassato.attuale()).isFalse();
        assertThat(periodoPassato.da()).isEqualTo(YearMonth.of(2018, 4));
        assertThat(periodoPassato.fonteInizio()).isEqualTo(FontePeriodo.DATA_CONTRATTUALE);
        assertThat(periodoPassato.a()).isEqualTo(YearMonth.of(2023, 12));
        assertThat(rapporti).extracting(RapportoSintesi::etichettaAzienda)
                .containsExactly("Beta Consulting S.p.A.", "Alfa Software S.r.l.");

        var dettaglio = consultazione.dettaglioRapporto(passato.id()).orElseThrow();
        assertThat(dettaglio.persona().id()).isEqualTo(mario.id());
        assertThat(dettaglio.azienda().id()).isEqualTo(alfa.id());
        assertThat(dettaglio.periodo()).isEqualTo(periodoPassato);
    }

    @Test
    void aziendeOmonimeDellaPersonaDistinteConLaPartitaIva() {
        var mario = mario();
        var alfa = alfa();
        var omonima = censimento.trovaOCreaAzienda(new DatiAzienda(null, PIVA_ALFA_OMONIMA, null,
                "ALFA SOFTWARE SRL", null, null));
        var beta = censimento.trovaOCreaAzienda(new DatiAzienda(null, PIVA_BETA, null, "Beta Consulting S.p.A.",
                null, null));
        rapporto(mario, alfa, null, LocalDate.of(2019, 12, 31), YearMonth.of(2018, 1), YearMonth.of(2019, 12));
        var secondo = rapporto(mario, omonima, null, null, YearMonth.of(2020, 1), YearMonth.of(2022, 12));
        rapporto(mario, beta, null, null, YearMonth.of(2023, 1), YearMonth.of(2025, 12));

        assertThat(consultazione.rapportiDiPersona(mario.id())).extracting(RapportoSintesi::etichettaAzienda)
                .containsExactly(
                        "Beta Consulting S.p.A.",
                        "ALFA SOFTWARE SRL (P.IVA " + PIVA_ALFA_OMONIMA + ")",
                        "Alfa Software S.r.l. (P.IVA " + PIVA_ALFA + ")");
        assertThat(consultazione.dettaglioRapporto(secondo.id())).get()
                .extracting(d -> d.etichettaAzienda())
                .isEqualTo("ALFA SOFTWARE SRL (P.IVA " + PIVA_ALFA_OMONIMA + ")");
    }

    @Test
    void rapportiDiPersonaInesistenteRifiutato() {
        assertThatThrownBy(() -> consultazione.rapportiDiPersona(new PersonaId(999_999)))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThat(consultazione.dettaglioRapporto(new RapportoLavoroId(999_999))).isEmpty();
    }

    @Test
    void impostaCcnlPubblicaLEventoSoloSeIlCodiceCambia(PublishedEvents eventi) {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                null, null, null, null, null, "COMMERCIO", YearMonth.of(2024, 1)));

        // stesso codice gia' riconosciuto: diventa scelto dall'utente, nessun ricalcolo
        var confermato = gestione.impostaCcnl(rapporto.id(), "COMMERCIO");
        assertThat(confermato.ccnlSceltoDaUtente()).isTrue();
        assertThat(eventi.ofType(CcnlRapportoModificato.class)).isEmpty();

        var cambiato = gestione.impostaCcnl(rapporto.id(), "TURISMO");
        assertThat(cambiato.ccnlCodice()).isEqualTo("TURISMO");
        assertThat(eventi.ofType(CcnlRapportoModificato.class))
                .containsExactly(new CcnlRapportoModificato(rapporto.id(), "COMMERCIO", "TURISMO"));

        gestione.impostaCcnl(rapporto.id(), "TURISMO");
        assertThat(eventi.ofType(CcnlRapportoModificato.class)).hasSize(1);

        // l'estrazione non sovrascrive piu' la scelta dell'utente
        var dopoCedolino = censimento.trovaOCreaRapporto(rapporto.personaId(), rapporto.aziendaId(),
                new DatiRapporto(null, null, null, null, null, "COMMERCIO", YearMonth.of(2024, 9)));
        assertThat(dopoCedolino.ccnlCodice()).isEqualTo("TURISMO");
    }

    @Test
    void primaSceltaDelCcnlDaIndicare(PublishedEvents eventi) {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                null, null, null, null, null, null, YearMonth.of(2024, 1)));
        assertThat(rapporto.ccnlDaIndicare()).isTrue();

        gestione.impostaCcnl(rapporto.id(), "METALMECCANICO_INDUSTRIA");

        assertThat(eventi.ofType(CcnlRapportoModificato.class))
                .containsExactly(new CcnlRapportoModificato(rapporto.id(), null, "METALMECCANICO_INDUSTRIA"));
    }

    @Test
    void ccnlORapportoInesistentiRifiutati() {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                null, null, null, null, null, null, YearMonth.of(2024, 1)));
        assertThatThrownBy(() -> gestione.impostaCcnl(rapporto.id(), "NON_ESISTE"))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThatThrownBy(() -> gestione.impostaCcnl(new RapportoLavoroId(999_999), "COMMERCIO"))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    @Test
    void noteSalvateSenzaSpaziAiBordiEVuoteComeNull() {
        var rapporto = censimento.trovaOCreaRapporto(mario().id(), alfa().id(), new DatiRapporto(
                null, null, null, null, null, null, YearMonth.of(2024, 1)));

        assertThat(gestione.aggiornaNote(rapporto.id(), "  Premio di produzione a luglio  ").note())
                .isEqualTo("Premio di produzione a luglio");
        assertThat(gestione.aggiornaNote(rapporto.id(), "   ").note()).isNull();
        assertThatThrownBy(() -> gestione.aggiornaNote(rapporto.id(), "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminaRapportoEPulisceAziendaEPersonaOrfane(PublishedEvents eventi) {
        var mario = mario();
        var alfa = alfa();
        var beta = censimento.trovaOCreaAzienda(new DatiAzienda(null, PIVA_BETA, null, "Beta Consulting S.p.A.",
                null, null));
        var giuseppina = censimento.trovaOCreaPersona(new DatiPersona(CF_GIUSEPPINA, "Giuseppina", "Verdi", null));
        var marioAlfa = rapporto(mario, alfa, null, null, YearMonth.of(2020, 1), YearMonth.of(2022, 12));
        var marioBeta = rapporto(mario, beta, null, null, YearMonth.of(2023, 1), YearMonth.of(2024, 12));
        rapporto(giuseppina, beta, null, null, YearMonth.of(2023, 1), YearMonth.of(2024, 12));

        censimento.eliminaRapporto(marioAlfa.id());

        assertThat(eventi.ofType(RapportoLavoroEliminato.class))
                .containsExactly(new RapportoLavoroEliminato(marioAlfa.id(), mario.id(), alfa.id()));
        assertThat(consultazione.trovaRapporto(marioAlfa.id())).isEmpty();
        assertThat(consultazione.trovaAzienda(alfa.id())).isEmpty();
        assertThat(consultazione.trovaPersona(mario.id())).isPresent();

        censimento.eliminaRapporto(marioBeta.id());

        assertThat(consultazione.trovaPersona(mario.id())).isEmpty();
        // Beta ha ancora il rapporto con Giuseppina
        assertThat(consultazione.trovaAzienda(beta.id())).isPresent();
        assertThat(consultazione.elencoPersone()).extracting(PersonaSintesi::id).containsExactly(giuseppina.id());

        // rapporto gia' eliminato: nessun effetto
        censimento.eliminaRapporto(marioBeta.id());
        assertThat(eventi.ofType(RapportoLavoroEliminato.class)).hasSize(2);
    }

    // ------------------------------------------------------------------------------------------ supporto

    private Persona mario() {
        return censimento.trovaOCreaPersona(new DatiPersona(CF_MARIO, "Mario", "Rossi", null));
    }

    private Azienda alfa() {
        return censimento.trovaOCreaAzienda(new DatiAzienda(null, PIVA_ALFA, null, "Alfa Software S.r.l.", null, null));
    }

    private RapportoLavoro rapporto(Persona persona, Azienda azienda, LocalDate assunzione, LocalDate cessazione,
                                    YearMonth primo, YearMonth ultimo) {
        var rapporto = censimento.trovaOCreaRapporto(persona.id(), azienda.id(),
                new DatiRapporto(assunzione, cessazione, null, null, null, null, ultimo));
        censimento.aggiornaEstremiCedolini(rapporto.id(), primo, ultimo);
        return consultazione.trovaRapporto(rapporto.id()).orElseThrow();
    }
}
