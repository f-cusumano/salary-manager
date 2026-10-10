package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.completo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conContratto;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDatore;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conPeriodo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conRetribuzione;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conTfr;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.essenziale;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.vuoto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.SchemaEstrazione;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cedolino inserito a mano come JSON dello schema di lettura: conformita' allo schema e andata e ritorno attraverso
 * l'interprete comune a tutti i lettori. Dati inventati.
 */
class JsonCedolinoManualeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final InterpreteRispostaEstrazione interprete = InterpreteRispostaEstrazione.standard(CLOCK);

    @Test
    void unCedolinoCompletoEUnoVuotoSonoConformiAlloSchema() {
        var schema = SchemaEstrazione.carica();

        for (var cedolino : List.of(completo(), essenziale(), vuoto(),
                ValidazioneCedolinoManuale.normalizza(vuoto()))) {
            var json = JsonCedolinoManuale.scrivi(cedolino);
            assertThat(schema.valida(MAPPER.readTree(json))).as(json).isEmpty();
        }
    }

    @Test
    void ilCedolinoVuotoHaLeConvenzioniDelloSchema() {
        var json = JsonCedolinoManuale.json(vuoto());

        assertThat(json.get("numeroCedoliniNelDocumento").asInt()).isEqualTo(1);
        assertThat(json.at("/periodo/anno").asInt()).isZero();
        assertThat(json.at("/periodo/mese").asInt()).isZero();
        assertThat(json.at("/dipendente/codiceFiscale").asString()).isEmpty();
        assertThat(json.at("/totali/netto").asString()).isEmpty();
        assertThat(json.at("/retribuzione/superminimoAssorbibile").asString()).isEqualTo("NON_INDICATO");
        assertThat(json.at("/tfr/fondo31DicembreAnnoPrecedente").asString()).isEmpty();
        assertThat(json.at("/buoniPasto/presenti").asBoolean()).isFalse();
        assertThat(json.at("/confidenza/complessiva").asInt()).isEqualTo(1);
        assertThat(json.get("campiNonTrovati")).isEmpty();
        assertThat(json.get("avvisi")).isEmpty();
    }

    @Test
    void gliImportiSonoScrittiInNotazioneDecimaleSemplice() {
        var cedolino = CedoliniManualiDiProva.conTotali(essenziale(), new CedolinoManuale.Totali(
                null, new java.math.BigDecimal("2.1E+3"), new java.math.BigDecimal("450.00"), null,
                new java.math.BigDecimal("1650.00")));

        var json = JsonCedolinoManuale.json(cedolino);

        assertThat(json.at("/totali/totaleCompetenze").asString()).isEqualTo("2100");
        assertThat(json.at("/totali/totaleTrattenute").asString()).isEqualTo("450.00");
    }

    @Test
    void andataERitornoDalLInterpreteRestituisceICampiInseriti() {
        var risultato = interprete.interpreta(JsonCedolinoManuale.scrivi(completo()), "inserimento-manuale-1");
        var c = risultato.cedolino();

        assertThat(risultato.avvisi()).as("nessun valore non interpretabile, controlli superati").isEmpty();
        assertThat(risultato.modello()).isEqualTo("inserimento-manuale-1");
        assertThat(c.numeroCedoliniNelDocumento()).isEqualTo(1);
        assertThat(c.dipendente()).isEqualTo(new CedolinoEstratto.Dipendente("RSSMRA80A01H501U", "Mario", "Rossi",
                "1980-01-01", "000123"));
        assertThat(c.datoreLavoro()).isEqualTo(new CedolinoEstratto.DatoreLavoro("01234567897", "01234567897",
                "1122334455", "Alfa Software S.r.l.", "Via Roma 1, 00100 Roma (RM)", "ALFA01"));
        assertThat(c.datiContrattuali()).isEqualTo(new CedolinoEstratto.DatiContrattuali("2021-09-01", null,
                "Impiegato - Programmatore", "5", "CCNL Metalmeccanica Industria (CNEL C011)"));
        assertThat(c.periodo()).isEqualTo(new CedolinoEstratto.Periodo(2023, 5, TipoCedolinoEstratto.ORDINARIA,
                "2023-05-26", 2023, 4));
        assertThat(c.totali().netto()).isEqualByComparingTo("1650.40");
        assertThat(c.totali().arrotondamento()).isEqualByComparingTo("0.40");
        assertThat(c.retribuzione().minimo()).isEqualByComparingTo("1800.00");
        assertThat(c.retribuzione().contingenza()).isNull();
        assertThat(c.retribuzione().superminimoAssorbibile()).isTrue();
        assertThat(c.retribuzione().divisoreOrario()).isEqualByComparingTo("173");
        assertThat(c.voci()).extracting(CedolinoEstratto.VoceEstratta::categoria).containsExactly(
                CategoriaVoce.COMPETENZA, CategoriaVoce.TRATTENUTA_PREVIDENZIALE, CategoriaVoce.TRATTENUTA_FISCALE,
                CategoriaVoce.PRESENZA);
        assertThat(c.voci().getFirst()).satisfies(v -> {
            assertThat(v.codice()).isEqualTo("7020");
            assertThat(v.descrizione()).isEqualTo("Retribuzione ordinaria");
            assertThat(v.quantita()).isEqualByComparingTo("26.00");
            assertThat(v.unita()).isEqualTo("giorni");
            assertThat(v.base()).isEqualByComparingTo("80.76923");
            assertThat(v.importoCompetenza()).isEqualByComparingTo("2100.00");
            assertThat(v.importoTrattenuta()).isNull();
        });
        assertThat(c.ratei()).extracting(r -> r.tipo() + "/" + r.unita()).containsExactly("FERIE/GIORNI", "ROL/ORE");
        assertThat(c.ratei().getFirst().saldo()).isEqualByComparingTo("6.16667");
        assertThat(c.tfr().fondo31DicembreAnnoPrecedente()).isEqualByComparingTo("5000.00");
        assertThat(c.tfr().quotaFondi()).isNull();
        assertThat(c.presenze().oreLavorate()).isEqualByComparingTo("168.00");
        assertThat(c.scatti()).isEqualTo(new CedolinoEstratto.Scatti(1, "2025-09-01"));
        assertThat(c.buoniPasto().presenti()).isTrue();
        assertThat(c.buoniPasto().importo()).isEqualByComparingTo("160.00");
        assertThat(c.progressivi().irpefTrattenuta()).isEqualByComparingTo("2000.00");
        assertThat(c.contieneTredicesima()).isFalse();
        assertThat(c.campiNonTrovati()).isEmpty();
    }

    @Test
    void ilSuperminimoAssorbibileHaTreValori() {
        for (var assorbibile : new Boolean[] {Boolean.TRUE, Boolean.FALSE, null}) {
            var r = completo().retribuzione();
            var cedolino = conRetribuzione(completo(), new ElementiRetributivi(r.minimo(), r.contingenza(),
                    r.superminimo(), assorbibile, r.importoScatti(), r.altriElementiFissi(), r.retribuzioneBase(),
                    r.totale(), r.pagaOraria(), r.pagaGiornaliera(), r.divisoreOrario(), r.divisoreGiornaliero()));

            var letto = interprete.interpreta(JsonCedolinoManuale.scrivi(cedolino), "inserimento-manuale-1");

            assertThat(letto.cedolino().retribuzione().superminimoAssorbibile()).isEqualTo(assorbibile);
        }
    }

    @Test
    void senzaDatiTfrIlTfrRestaNullESenzaCnelIlCcnlNonCambia() {
        var k = completo().datiContrattuali();
        var cedolino = conContratto(conTfr(completo(), null), new CedolinoManuale.DatiContrattuali(
                k.dataAssunzione(), k.dataCessazione(), k.qualifica(), k.livello(), k.ccnl(), null));

        var c = interprete.interpreta(JsonCedolinoManuale.scrivi(cedolino), "inserimento-manuale-1").cedolino();

        assertThat(c.tfr()).isNull();
        assertThat(c.datiContrattuali().ccnl()).isEqualTo("CCNL Metalmeccanica Industria");
    }

    @Test
    void unCcnlIndicatoSoloConIlCodiceCnelDiventaIlTestoDelCcnl() {
        var k = completo().datiContrattuali();
        var cedolino = conContratto(completo(), new CedolinoManuale.DatiContrattuali(k.dataAssunzione(),
                k.dataCessazione(), k.qualifica(), k.livello(), null, "C011"));

        var c = interprete.interpreta(JsonCedolinoManuale.scrivi(cedolino), "inserimento-manuale-1").cedolino();

        assertThat(c.datiContrattuali().ccnl()).isEqualTo("CNEL C011");
    }

    @Test
    void tipoTredicesimaEMeseVariabiliAssenteArrivanoAllInterprete() {
        var p = completo().periodo();
        var cedolino = ValidazioneCedolinoManuale.normalizza(conPeriodo(completo(), new CedolinoManuale.Periodo(
                2023, 12, TipoMensilita.TREDICESIMA, p.dataElaborazione(), null)));

        var c = interprete.interpreta(JsonCedolinoManuale.scrivi(cedolino), "inserimento-manuale-1").cedolino();

        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.TREDICESIMA);
        assertThat(c.periodo().annoVariabili()).isNull();
        assertThat(c.periodo().meseVariabili()).isNull();
        assertThat(c.contieneTredicesima()).isTrue();
    }

    @Test
    void unIbanScrittoInUnTestoVieneMascherato() {
        var a = completo().datoreLavoro();
        var cedolino = conDatore(completo(), new CedolinoManuale.DatoreLavoro(a.codiceFiscale(), a.partitaIva(),
                a.posizioneInps(), a.posizioneInail(), a.ragioneSociale(),
                "Via Roma 1 - IBAN IT60 X054 2811 1010 0000 0123 456", a.codiceAzienda()));

        var risultato = interprete.interpreta(JsonCedolinoManuale.scrivi(cedolino), "inserimento-manuale-1");

        assertThat(risultato.cedolino().datoreLavoro().indirizzo())
                .isEqualTo("Via Roma 1 - IBAN [dato bancario rimosso]");
        assertThat(risultato.jsonOriginale()).doesNotContain("IT60", "X054", "0123 456");
        assertThat(risultato.avvisi()).contains("Rimossi dati bancari presenti nell'estrazione: non vengono salvati");
    }

    @Test
    void unaVoceSenzaCategoriaOUnRateoSenzaTipoNonVengonoScritti() {
        var senzaCategoria = CedoliniManualiDiProva.conVoci(completo(),
                List.of(new Voce(null, "Voce di prova", null, null, null, null, null, null)));
        var senzaTipo = CedoliniManualiDiProva.conRatei(completo(), List.of(new com.fcusumano.sam.cedolini.Rateo(
                null, UnitaMisura.ORE, null, null, null, null)));

        assertThatThrownBy(() -> JsonCedolinoManuale.scrivi(senzaCategoria)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JsonCedolinoManuale.scrivi(senzaTipo)).isInstanceOf(IllegalStateException.class);
        assertThat(TipoRateo.values()).isNotEmpty();
        assertThat(LocalDate.of(2023, 5, 1)).isNotNull();
    }
}
