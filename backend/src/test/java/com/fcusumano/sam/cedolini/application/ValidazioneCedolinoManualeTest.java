package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.CF_MARIO;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.PIVA_ALFA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.completo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conBuoniPasto;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conContratto;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDatore;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDipendente;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conPeriodo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conPresenze;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conProgressivi;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conRatei;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conRetribuzione;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conScatti;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conTfr;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conTotali;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conVoci;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.essenziale;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.n;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.vuoto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regole bloccanti, normalizzazione e avvisi dei cedolini inseriti a mano. Orologio fisso a gennaio 2026: l'anno di
 * competenza massimo e' il 2027. Dati inventati.
 */
class ValidazioneCedolinoManualeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String LUNGO_101 = "A".repeat(101);

    private final ValidazioneCedolinoManuale validazione = new ValidazioneCedolinoManuale(CLOCK);

    // ------------------------------------------------------------------------------------------ errori bloccanti

    @Test
    void unCedolinoCompletoOEssenzialeNonHaErrori() {
        assertThat(validazione.verifica(completo(), Set.of())).isEmpty();
        assertThat(validazione.verifica(essenziale(), Set.of())).isEmpty();
    }

    @Test
    void unCedolinoVuotoHaGliErroriDeiCampiObbligatoriNellOrdineDelloSchema() {
        assertThat(validazione.verifica(vuoto(), Set.of())).extracting(ErroreCampo::campo).containsExactly(
                "dipendente.codiceFiscale", "dipendente.nome", "dipendente.cognome", "datoreLavoro.ragioneSociale",
                "periodo.anno", "periodo.mese", "periodo.tipo", "totali.totaleCompetenze", "totali.totaleTrattenute",
                "totali.netto");
    }

    static Stream<Arguments> regoleBloccanti() {
        return Stream.of(
                regola("codice fiscale mancante", c -> conDipendente(c, dipendente("  ", "Mario", "Rossi")),
                        "dipendente.codiceFiscale", "Indica il codice fiscale del dipendente"),
                regola("codice fiscale con il carattere di controllo errato",
                        c -> conDipendente(c, dipendente("RSSMRA80A01H501X", "Mario", "Rossi")),
                        "dipendente.codiceFiscale", "Codice fiscale non valido: controlla i 16 caratteri"),
                regola("codice fiscale troppo corto", c -> conDipendente(c, dipendente("RSSMRA80A01", "Mario", "Rossi")),
                        "dipendente.codiceFiscale", "Codice fiscale non valido: controlla i 16 caratteri"),
                regola("nome mancante", c -> conDipendente(c, dipendente(CF_MARIO, " ", "Rossi")),
                        "dipendente.nome", "Indica il nome"),
                regola("cognome mancante", c -> conDipendente(c, dipendente(CF_MARIO, "Mario", null)),
                        "dipendente.cognome", "Indica il cognome"),
                regola("nome troppo lungo", c -> conDipendente(c, dipendente(CF_MARIO, LUNGO_101, "Rossi")),
                        "dipendente.nome", "Testo troppo lungo (massimo 100 caratteri)"),
                regola("data di nascita nel futuro", c -> conDipendente(c, new CedolinoManuale.Dipendente(CF_MARIO,
                                "Mario", "Rossi", LocalDate.of(2026, 2, 1), null)),
                        "dipendente.dataNascita", "La data di nascita è nel futuro"),
                regola("data di nascita prima del 1900", c -> conDipendente(c, new CedolinoManuale.Dipendente(CF_MARIO,
                                "Mario", "Rossi", LocalDate.of(1899, 12, 31), null)),
                        "dipendente.dataNascita", "Data non valida (anno dal 1900 al 2999)"),
                regola("codice fiscale dell'azienda di 12 cifre", c -> conDatore(c, datore("012345678971", PIVA_ALFA,
                                "Alfa Software S.r.l.")),
                        "datoreLavoro.codiceFiscale", "Codice fiscale dell'azienda non valido"),
                regola("partita IVA di 10 cifre", c -> conDatore(c, datore(null, "0123456789", "Alfa Software S.r.l.")),
                        "datoreLavoro.partitaIva", "Partita IVA non valida: deve avere 11 cifre"),
                regola("partita IVA con lettere", c -> conDatore(c, datore(null, "0123456789A", "Alfa Software S.r.l.")),
                        "datoreLavoro.partitaIva", "Partita IVA non valida: deve avere 11 cifre"),
                regola("ragione sociale mancante", c -> conDatore(c, datore(null, PIVA_ALFA, null)),
                        "datoreLavoro.ragioneSociale", "Indica la ragione sociale"),
                regola("ragione sociale di soli simboli", c -> conDatore(c, datore(null, PIVA_ALFA, " - . ")),
                        "datoreLavoro.ragioneSociale", "Indica la ragione sociale"),
                regola("ragione sociale con caratteri di controllo",
                        c -> conDatore(c, datore(null, PIVA_ALFA, "Alfa\u0007Software")),
                        "datoreLavoro.ragioneSociale", "Il testo contiene caratteri non ammessi"),
                regola("cessazione prima dell'assunzione", c -> conContratto(c, new CedolinoManuale.DatiContrattuali(
                                LocalDate.of(2021, 9, 1), LocalDate.of(2021, 8, 31), null, null, null, null)),
                        "datiContrattuali.dataCessazione", "La data di cessazione precede l'assunzione"),
                regola("assunzione nell'anno 3000", c -> conContratto(c, new CedolinoManuale.DatiContrattuali(
                                LocalDate.of(3000, 1, 1), null, null, null, null, null)),
                        "datiContrattuali.dataAssunzione", "Data non valida (anno dal 1900 al 2999)"),
                regola("codice CNEL troppo lungo", c -> conContratto(c, new CedolinoManuale.DatiContrattuali(
                                null, null, null, null, null, "C".repeat(21))),
                        "datiContrattuali.codiceCnel", "Testo troppo lungo (massimo 20 caratteri)"),
                regola("anno mancante", c -> conPeriodo(c, periodo(null, 5, TipoMensilita.ORDINARIA)),
                        "periodo.anno", "Indica l'anno (dal 1900 al 2027)"),
                regola("anno prima del 1900", c -> conPeriodo(c, periodo(1899, 5, TipoMensilita.ORDINARIA)),
                        "periodo.anno", "Indica l'anno (dal 1900 al 2027)"),
                regola("anno oltre il prossimo", c -> conPeriodo(c, periodo(2028, 5, TipoMensilita.ORDINARIA)),
                        "periodo.anno", "Indica l'anno (dal 1900 al 2027)"),
                regola("mese 13", c -> conPeriodo(c, periodo(2023, 13, TipoMensilita.TREDICESIMA)), "periodo.mese",
                        "Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo"),
                regola("mese 0", c -> conPeriodo(c, periodo(2023, 0, TipoMensilita.ORDINARIA)), "periodo.mese",
                        "Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo"),
                regola("tipo mancante", c -> conPeriodo(c, periodo(2023, 5, null)),
                        "periodo.tipo", "Indica il tipo di cedolino"),
                regola("mese delle variabili nell'anno 3000", c -> conPeriodo(c, new CedolinoManuale.Periodo(2023, 5,
                                TipoMensilita.ORDINARIA, null, YearMonth.of(3000, 1))),
                        "periodo.meseVariabili", "Data non valida (anno dal 1900 al 2999)"),
                regola("totale competenze mancante",
                        c -> conTotali(c, new CedolinoManuale.Totali(null, null, n("450.00"), null, n("1650.00"))),
                        "totali.totaleCompetenze", "Indica il totale competenze"),
                regola("totale trattenute mancante",
                        c -> conTotali(c, new CedolinoManuale.Totali(null, n("2100.00"), null, null, n("1650.00"))),
                        "totali.totaleTrattenute", "Indica il totale trattenute"),
                regola("netto mancante",
                        c -> conTotali(c, new CedolinoManuale.Totali(null, n("2100.00"), n("450.00"), null, null)),
                        "totali.netto", "Indica il netto"),
                regola("totale competenze negativo", c -> conTotali(c,
                                new CedolinoManuale.Totali(null, n("-2100.00"), n("450.00"), null, n("1650.00"))),
                        "totali.totaleCompetenze", "Indica l'importo senza segno"),
                regola("totale trattenute negativo", c -> conTotali(c,
                                new CedolinoManuale.Totali(null, n("2100.00"), n("-450.00"), null, n("1650.00"))),
                        "totali.totaleTrattenute", "Indica l'importo senza segno"),
                regola("netto di 11 cifre intere", c -> conTotali(c,
                                new CedolinoManuale.Totali(null, n("2100.00"), n("450.00"), null, n("10000000000"))),
                        "totali.netto", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("lordo con 6 decimali", c -> conTotali(c,
                                new CedolinoManuale.Totali(n("2100.000001"), n("2100.00"), n("450.00"), null,
                                        n("1650.00"))),
                        "totali.lordo", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("paga oraria fuori dai limiti", c -> conRetribuzione(c, new ElementiRetributivi(null, null,
                                null, null, null, null, null, null, n("-10000000000"), null, null, null)),
                        "retribuzione.pagaOraria", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("voce senza descrizione", c -> conVoci(c, List.of(new Voce("7020", "  ",
                                CategoriaVoce.COMPETENZA, null, null, null, n("2100.00"), null))),
                        "voci[0].descrizione", "Indica la descrizione della voce"),
                regola("voce senza categoria", c -> conVoci(c, List.of(new Voce(null, "Retribuzione ordinaria", null,
                                null, null, null, n("2100.00"), null))),
                        "voci[0].categoria", "Indica la categoria della voce"),
                regola("trattenuta di una voce negativa", c -> conVoci(c, List.of(c.voci().getFirst(), new Voce(null,
                                "Ritenuta IRPEF", CategoriaVoce.TRATTENUTA_FISCALE, null, null, null, null,
                                n("-450.00")))),
                        "voci[1].importoTrattenuta", "Indica l'importo senza segno"),
                regola("codice di una voce troppo lungo", c -> conVoci(c, List.of(new Voce("7".repeat(31),
                                "Retribuzione ordinaria", CategoriaVoce.COMPETENZA, null, null, null, n("2100.00"),
                                null))),
                        "voci[0].codice", "Testo troppo lungo (massimo 30 caratteri)"),
                regola("rateo senza tipo", c -> conRatei(c, List.of(new Rateo(null, UnitaMisura.GIORNI, null,
                                n("2.16667"), null, null))),
                        "ratei[0].tipo", "Indica il tipo del rateo"),
                regola("rateo senza unita'", c -> conRatei(c, List.of(new Rateo(TipoRateo.FERIE, null, null,
                                n("2.16667"), null, null))),
                        "ratei[0].unita", "Indica l'unità del rateo"),
                regola("rateo ripetuto", c -> conRatei(c, List.of(
                                new Rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, null, n("2.16667"), null, null),
                                new Rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, null, null, n("1.00"), null))),
                        "ratei[1]", "Rateo ripetuto: uno solo per tipo e unità"),
                regola("rateo senza valori", c -> conRatei(c, List.of(
                                new Rateo(TipoRateo.ROL, UnitaMisura.ORE, null, null, null, null))),
                        "ratei[0]", "Indica almeno un valore del rateo"),
                regola("quota TFR fuori dai limiti", c -> conTfr(c, new DatiTfr(null, null, null, null,
                                n("0.000001"), null, null, null, null, null, null)),
                        "tfr.quotaMese", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("ore lavorate fuori dai limiti",
                        c -> conPresenze(c, new Presenze(null, n("99999999999"), null)),
                        "presenze.oreLavorate", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("100 scatti", c -> conScatti(c, new CedolinoManuale.Scatti(100, null)),
                        "scatti.numeroScatti", "Numero di scatti non valido"),
                regola("scatti negativi", c -> conScatti(c, new CedolinoManuale.Scatti(-1, null)),
                        "scatti.numeroScatti", "Numero di scatti non valido"),
                regola("prossimo scatto nell'anno 3000",
                        c -> conScatti(c, new CedolinoManuale.Scatti(1, LocalDate.of(3000, 1, 1))),
                        "scatti.dataProssimoScatto", "Data non valida (anno dal 1900 al 2999)"),
                regola("buoni pasto fuori dai limiti",
                        c -> conBuoniPasto(c, new BuoniPasto(true, n("20"), n("8.0000001"), null)),
                        "buoniPasto.valoreUnitario", "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"),
                regola("progressivo fuori dai limiti",
                        c -> conProgressivi(c, new ProgressiviAnnui(null, null, n("10000000000.5"))),
                        "progressivi.irpefTrattenuta",
                        "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("regoleBloccanti")
    void ogniRegolaBloccanteIndicaIlCampo(String caso, UnaryOperator<CedolinoManuale> variante, String campo,
            String messaggio) {
        assertThat(validazione.verifica(variante.apply(essenziale()), Set.of()))
                .containsExactly(new ErroreCampo(campo, messaggio));
    }

    @Test
    void unaVoceAssenteNellElencoEUnaVoceVuota() {
        var cedolino = conVoci(essenziale(), Arrays.asList(essenziale().voci().getFirst(), null));

        assertThat(validazione.verifica(cedolino, Set.of())).containsExactly(
                new ErroreCampo("voci[1].descrizione", "Indica la descrizione della voce"),
                new ErroreCampo("voci[1].categoria", "Indica la categoria della voce"));
    }

    @Test
    void unCampoHaUnSoloErrore() {
        var cedolino = conTotali(essenziale(),
                new CedolinoManuale.Totali(null, n("-10000000000"), n("450.00"), null, n("1650.00")));

        assertThat(validazione.verifica(cedolino, Set.of()))
                .containsExactly(new ErroreCampo("totali.totaleCompetenze", "Indica l'importo senza segno"));
    }

    @Test
    void nettoEArrotondamentoPossonoEssereNegativi() {
        var cedolino = conTotali(essenziale(),
                new CedolinoManuale.Totali(null, n("100.00"), n("150.00"), n("-0.40"), n("-50.40")));

        assertThat(validazione.verifica(cedolino, Set.of())).isEmpty();
    }

    @Test
    void iMessaggiNonRipetonoIValoriInseriti() {
        var valori = List.of("RSSMRA80A01H501X", "012345678971", "0123456789", "1899", LUNGO_101, "-2100.00");
        var cedolino = conTotali(conPeriodo(conDatore(conDipendente(essenziale(),
                                dipendente("RSSMRA80A01H501X", LUNGO_101, "Rossi")),
                        datore("012345678971", "0123456789", "Alfa Software S.r.l.")),
                        periodo(1899, 5, TipoMensilita.ORDINARIA)),
                new CedolinoManuale.Totali(null, n("-2100.00"), n("450.00"), null, n("1650.00")));

        var errori = validazione.verifica(cedolino, Set.of());

        assertThat(errori).hasSize(6);
        assertThat(errori).allSatisfy(e -> assertThat(valori).noneMatch(v -> e.messaggio().contains(v)));
    }

    @Test
    void unCodiceFiscaleFormalmenteErratoEAccettatoSeLaPersonaECensita() {
        var cedolino = conDipendente(essenziale(), dipendente(" rssmra80a01h501x ", "Mario", "Rossi"));

        assertThat(validazione.verifica(cedolino, Set.of("RSSMRA80A01H501X"))).isEmpty();
        assertThat(validazione.verifica(cedolino, Set.of(CF_MARIO))).extracting(ErroreCampo::campo)
                .containsExactly("dipendente.codiceFiscale");
    }

    @Test
    void unCodiceFiscaleProvvisorioEAccettatoConUnAvviso() {
        var cedolino = conDipendente(completo(), dipendente("12345678901", "Mario", "Rossi"));

        assertThat(validazione.verifica(cedolino, Set.of())).isEmpty();
        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(cedolino)))
                .containsExactly(ValidazioneCedolinoManuale.AVVISO_CODICE_FISCALE_PROVVISORIO);
    }

    @Test
    void laValidazioneAvvieneSuiDatiNormalizzati() {
        var cedolino = conDatore(conDipendente(essenziale(), dipendente("rssmra 80a01 h501u", "Mario", "Rossi")),
                datore("it" + PIVA_ALFA, "IT " + PIVA_ALFA, "  Alfa Software S.r.l.  "));

        assertThat(validazione.verifica(cedolino, Set.of())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ normalizzazione

    @Test
    void laNormalizzazioneRipulisceTestiECodici() {
        var cedolino = conVoci(conContratto(conDatore(conDipendente(essenziale(),
                                new CedolinoManuale.Dipendente(" rssmra80a01h501u", "  Mario   Luigi ", "Rossi\t",
                                        null, "   ")),
                        datore("it" + PIVA_ALFA, "IT " + PIVA_ALFA, "Alfa   Software\nS.r.l.")),
                        new CedolinoManuale.DatiContrattuali(null, null, " Impiegato ", "", null, null)),
                List.of(new Voce(" 7020 ", " Retribuzione  ordinaria ", CategoriaVoce.COMPETENZA, null, " ", null,
                        n("2100.00"), null)));

        var normalizzato = ValidazioneCedolinoManuale.normalizza(cedolino);

        assertThat(normalizzato.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(CF_MARIO, "Mario Luigi", "Rossi",
                null, null));
        assertThat(normalizzato.datoreLavoro().codiceFiscale()).isEqualTo(PIVA_ALFA);
        assertThat(normalizzato.datoreLavoro().partitaIva()).isEqualTo(PIVA_ALFA);
        assertThat(normalizzato.datoreLavoro().ragioneSociale()).isEqualTo("Alfa Software S.r.l.");
        assertThat(normalizzato.datiContrattuali().qualifica()).isEqualTo("Impiegato");
        assertThat(normalizzato.datiContrattuali().livello()).isNull();
        assertThat(normalizzato.voci()).containsExactly(new Voce("7020", "Retribuzione ordinaria", CategoriaVoce.COMPETENZA,
                null, null, null, n("2100.00"), null));
        assertThat(ValidazioneCedolinoManuale.normalizza(normalizzato)).isEqualTo(normalizzato);
    }

    @Test
    void laNormalizzazioneCompletaISottoRecordTranneIlTfr() {
        var normalizzato = ValidazioneCedolinoManuale.normalizza(vuoto());

        assertThat(normalizzato.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(null, null, null, null, null));
        assertThat(normalizzato.periodo()).isEqualTo(new CedolinoManuale.Periodo(null, null, null, null, null));
        assertThat(normalizzato.retribuzione()).isEqualTo(ElementiRetributivi.VUOTI);
        assertThat(normalizzato.presenze()).isEqualTo(Presenze.VUOTE);
        assertThat(normalizzato.scatti()).isEqualTo(new CedolinoManuale.Scatti(null, null));
        assertThat(normalizzato.buoniPasto()).isEqualTo(BuoniPasto.ASSENTI);
        assertThat(normalizzato.progressivi()).isEqualTo(ProgressiviAnnui.VUOTI);
        assertThat(normalizzato.voci()).isEmpty();
        assertThat(normalizzato.ratei()).isEmpty();
        assertThat(normalizzato.tfr()).isNull();
    }

    @Test
    void ilTipoDeterminaIFlagDiTredicesimaEQuattordicesima() {
        var tredicesima = ValidazioneCedolinoManuale.normalizza(
                conPeriodo(essenziale(), periodo(2023, 12, TipoMensilita.TREDICESIMA)));
        var quattordicesima = ValidazioneCedolinoManuale.normalizza(
                conPeriodo(essenziale(), periodo(2023, 6, TipoMensilita.QUATTORDICESIMA)));
        var ordinaria = ValidazioneCedolinoManuale.normalizza(essenziale());

        assertThat(tredicesima.contieneTredicesima()).isTrue();
        assertThat(tredicesima.contieneQuattordicesima()).isFalse();
        assertThat(quattordicesima.contieneQuattordicesima()).isTrue();
        assertThat(quattordicesima.contieneTredicesima()).isFalse();
        assertThat(ordinaria.contieneTredicesima()).isFalse();
        assertThat(ordinaria.contieneQuattordicesima()).isFalse();
    }

    @Test
    void iBuoniPastoNonPresentiPerdonoGliImporti() {
        var cedolino = conBuoniPasto(essenziale(), new BuoniPasto(false, n("20"), n("8.00"), n("160.00")));

        assertThat(ValidazioneCedolinoManuale.normalizza(cedolino).buoniPasto()).isEqualTo(BuoniPasto.ASSENTI);
    }

    // ------------------------------------------------------------------------------------------ avvisi

    @Test
    void conVociETotaliCoerentiNonCiSonoAvvisi() {
        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(completo()))).isEmpty();
    }

    @Test
    void laSommaDelleVociDiversaDaiTotaliEUnAvviso() {
        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(essenziale())))
                .containsExactly(ValidazioneCedolinoManuale.AVVISO_SOMMA_TRATTENUTE);

        var competenzeDiverse = conVoci(completo(), voci("2099.94", "450.00"));
        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(competenzeDiverse)))
                .containsExactly(ValidazioneCedolinoManuale.AVVISO_SOMMA_COMPETENZE);
    }

    @Test
    void laSommaDelleVociHaUnaTolleranzaDiCinqueCentesimi() {
        var cedolino = conVoci(completo(), voci("2099.95", "450.05"));

        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(cedolino))).isEmpty();
    }

    @Test
    void leVociInformativeEDiPresenzaNonEntranoNelleSomme() {
        var voci = new ArrayList<>(voci("2100.00", "450.00"));
        voci.add(new Voce(null, "Imponibile previdenziale", CategoriaVoce.INFORMATIVA, null, null, null,
                n("2100.00"), null));
        voci.add(new Voce(null, "Ferie godute", CategoriaVoce.PRESENZA, n("1.00"), "giorni", null, null,
                n("80.77")));

        var cedolino = conVoci(completo(), voci);

        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(cedolino))).isEmpty();
    }

    @Test
    void senzaVociConImportoNonSiConfrontanoLeSomme() {
        var soloPresenze = conVoci(completo(), List.of(new Voce(null, "Ferie godute", CategoriaVoce.PRESENZA,
                n("1.00"), "giorni", null, null, null)));

        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(soloPresenze))).isEmpty();
        assertThat(ValidazioneCedolinoManuale.avvisi(ValidazioneCedolinoManuale.normalizza(conVoci(completo(),
                List.of())))).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ supporto

    private static Arguments regola(String caso, UnaryOperator<CedolinoManuale> variante, String campo,
            String messaggio) {
        return Arguments.of(caso, variante, campo, messaggio);
    }

    private static CedolinoManuale.Dipendente dipendente(String codiceFiscale, String nome, String cognome) {
        return new CedolinoManuale.Dipendente(codiceFiscale, nome, cognome, null, null);
    }

    private static CedolinoManuale.DatoreLavoro datore(String codiceFiscale, String partitaIva,
            String ragioneSociale) {
        return new CedolinoManuale.DatoreLavoro(codiceFiscale, partitaIva, null, null, ragioneSociale, null, null);
    }

    private static CedolinoManuale.Periodo periodo(Integer anno, Integer mese, TipoMensilita tipo) {
        return new CedolinoManuale.Periodo(anno, mese, tipo, null, null);
    }

    /** Una competenza e una trattenuta (i totali di {@link CedoliniManualiDiProva#completo()} sono 2100 e 450). */
    private static List<Voce> voci(String competenza, String trattenuta) {
        return List.of(
                new Voce("7020", "Retribuzione ordinaria", CategoriaVoce.COMPETENZA, null, null, null,
                        n(competenza), null),
                new Voce("8000", "Ritenuta IRPEF", CategoriaVoce.TRATTENUTA_FISCALE, null, null, null, null,
                        n(trattenuta)));
    }
}
