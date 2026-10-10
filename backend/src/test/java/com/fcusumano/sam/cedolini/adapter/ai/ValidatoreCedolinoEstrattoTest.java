package com.fcusumano.sam.cedolini.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.ConfidenzaCampo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.ValidatoreCedolinoEstratto;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Controlli semantici: un controllo non superato abbassa la confidenza e aggiunge un avviso, senza scartare.
 * Dati inventati.
 */
class ValidatoreCedolinoEstrattoTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private final ValidatoreCedolinoEstratto validatore = new ValidatoreCedolinoEstratto(CLOCK);

    private static CedolinoEstratto base(String fixture) throws IOException {
        // il validatore e' gia' passato sulla fixture: nessun avviso
        var risultato = InterpreteRispostaEstrazione.standard(CLOCK).interpreta(ClaudeLettoreCedoliniTest.fixture(fixture), "m");
        assertThat(risultato.avvisi()).noneMatch(a -> !a.startsWith("Nota estrazione"));
        return risultato.cedolino();
    }

    private static CedolinoEstratto con(CedolinoEstratto c, UnaryOperator<Copia> modifica) {
        return modifica.apply(new Copia(c)).build();
    }

    /** Copia modificabile del record per i test. */
    private static final class Copia {
        CedolinoEstratto.Dipendente dipendente;
        CedolinoEstratto.DatoreLavoro datoreLavoro;
        CedolinoEstratto.DatiContrattuali datiContrattuali;
        CedolinoEstratto.Periodo periodo;
        CedolinoEstratto.Totali totali;
        List<CedolinoEstratto.RateoEstratto> ratei;
        Boolean contieneTredicesima;
        CedolinoEstratto.Confidenza confidenza;
        private final CedolinoEstratto c;

        Copia(CedolinoEstratto c) {
            this.c = c;
            dipendente = c.dipendente();
            datoreLavoro = c.datoreLavoro();
            datiContrattuali = c.datiContrattuali();
            periodo = c.periodo();
            totali = c.totali();
            ratei = c.ratei();
            contieneTredicesima = c.contieneTredicesima();
            confidenza = c.confidenza();
        }

        CedolinoEstratto build() {
            return new CedolinoEstratto(c.numeroCedoliniNelDocumento(), dipendente, datoreLavoro, datiContrattuali, periodo,
                    totali, c.retribuzione(), c.voci(), ratei, c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                    c.progressivi(), contieneTredicesima, c.contieneQuattordicesima(), confidenza, c.campiNonTrovati());
        }
    }

    private static BigDecimal confidenzaCampo(CedolinoEstratto c, String campo) {
        return c.confidenza().campi().stream().filter(cc -> cc.campo().equals(campo)).map(ConfidenzaCampo::valore)
                .findFirst().orElseThrow();
    }

    @Test
    void unCedolinoCoerenteNonGeneraAvvisiENonCambiaLaConfidenza() throws IOException {
        var c = base("rossi-alfa-2024-tfr01");

        var verifica = validatore.verifica(c);

        assertThat(verifica.avvisi()).isEmpty();
        assertThat(verifica.cedolino()).isSameAs(c);
    }

    @Test
    void ilNettoIncoerenteAbbassaLaConfidenzaSenzaScartare() throws IOException {
        var c = base("rossi-alfa-2023-07");
        var t = c.totali();
        var incoerente = con(c, x -> {
            x.totali = new CedolinoEstratto.Totali(t.lordo(), t.totaleCompetenze(), t.totaleTrattenute(), t.arrotondamento(),
                    t.netto().add(new BigDecimal("50")));
            return x;
        });

        var verifica = validatore.verifica(incoerente);

        assertThat(verifica.avvisi()).containsExactly("Totale competenze meno totale trattenute non corrisponde al netto");
        assertThat(verifica.cedolino().confidenza().complessiva()).isEqualByComparingTo("0.76000");
        assertThat(confidenzaCampo(verifica.cedolino(), "totali.netto")).isEqualByComparingTo("0.5");
        assertThat(confidenzaCampo(verifica.cedolino(), "periodo.anno")).isEqualByComparingTo("0.99");
        assertThat(verifica.cedolino().totali().netto()).isEqualTo(incoerente.totali().netto());
    }

    @Test
    void ilNettoEntroLArrotondamentoECoerente() throws IOException {
        var c = base("rossi-alfa-2023-07");
        var t = c.totali();
        var senzaArrotondamento = con(c, x -> {
            x.totali = new CedolinoEstratto.Totali(t.lordo(), t.totaleCompetenze(), t.totaleTrattenute(), null, t.netto());
            return x;
        });

        assertThat(validatore.verifica(senzaArrotondamento).avvisi()).isEmpty();
    }

    @Test
    void annoMeseECodiceFiscaleNonPlausibili() throws IOException {
        var c = base("rossi-beta-2024-10");
        var p = c.periodo();
        var d = c.dipendente();
        var errato = con(c, x -> {
            x.periodo = new CedolinoEstratto.Periodo(2031, 13, p.tipo(), p.dataElaborazione(), null, null);
            x.dipendente = new CedolinoEstratto.Dipendente("RSSMRA80A01H501X", d.nome(), d.cognome(), d.dataNascita(),
                    d.codiceDipendente());
            return x;
        });

        var verifica = validatore.verifica(errato);

        assertThat(verifica.avvisi()).containsExactlyInAnyOrder(
                "Anno del periodo di retribuzione non plausibile",
                "Mese del periodo di retribuzione non valido (atteso 1..12)",
                "Codice fiscale del dipendente formalmente non valido");
        assertThat(confidenzaCampo(verifica.cedolino(), "dipendente.codiceFiscale")).isEqualByComparingTo("0.5");
        assertThat(verifica.cedolino().confidenza().complessiva()).isEqualByComparingTo("0.47616");
    }

    @Test
    void codiciFiscaliOmocodiciEPartiteIvaFormalmenteValide() throws IOException {
        var c = base("rossi-beta-2024-10");
        var d = c.dipendente();
        var a = c.datoreLavoro();
        // omocodia: la cifra 0 di "80" sostituita da L; carattere di controllo ricalcolato
        var omocodico = con(c, x -> {
            x.dipendente = new CedolinoEstratto.Dipendente("RSSMRA8LA01H501F", d.nome(), d.cognome(), d.dataNascita(),
                    d.codiceDipendente());
            x.datoreLavoro = new CedolinoEstratto.DatoreLavoro("09876543217", "09876543217", a.posizioneInps(),
                    a.ragioneSociale(), a.indirizzo(), a.codiceAzienda());
            return x;
        });
        assertThat(validatore.verifica(omocodico).avvisi()).isEmpty();

        var pivaErrata = con(c, x -> {
            x.datoreLavoro = new CedolinoEstratto.DatoreLavoro(null, "09876543210", a.posizioneInps(), a.ragioneSociale(),
                    a.indirizzo(), a.codiceAzienda());
            return x;
        });
        assertThat(validatore.verifica(pivaErrata).avvisi()).containsExactly("Partita IVA dell'azienda formalmente non valida");
    }

    @Test
    void fineRapportoDeveSeguireLaCessazione() throws IOException {
        var c = base("rossi-alfa-2024-tfr01");
        var dc = c.datiContrattuali();
        var p = c.periodo();
        var senzaCessazione = con(c, x -> {
            x.datiContrattuali = new CedolinoEstratto.DatiContrattuali(dc.dataAssunzione(), null, dc.qualifica(), dc.livello(),
                    dc.ccnl());
            return x;
        });
        var nelMeseDiCessazione = con(c, x -> {
            x.periodo = new CedolinoEstratto.Periodo(2024, 6, TipoCedolinoEstratto.FINE_RAPPORTO, p.dataElaborazione(), null,
                    null);
            return x;
        });
        var cessazionePrimaDellAssunzione = con(c, x -> {
            x.datiContrattuali = new CedolinoEstratto.DatiContrattuali("2024-07-01", "2024-06-15", dc.qualifica(),
                    dc.livello(), dc.ccnl());
            return x;
        });

        assertThat(validatore.verifica(senzaCessazione).avvisi())
                .containsExactly("Cedolino di fine rapporto senza data di cessazione stampata");
        assertThat(validatore.verifica(nelMeseDiCessazione).avvisi())
                .containsExactly("Cedolino di fine rapporto con periodo non successivo al mese di cessazione");
        assertThat(validatore.verifica(cessazionePrimaDellAssunzione).avvisi())
                .containsExactly("Data di cessazione precedente all'assunzione");
    }

    @Test
    void tredicesimaSenzaVoceESaldoDeiRateiIncoerente() throws IOException {
        var c = base("rossi-alfa-2023-13");
        var r = c.ratei().getFirst();
        var errato = con(c, x -> {
            x.contieneTredicesima = false;
            x.ratei = List.of(new CedolinoEstratto.RateoEstratto(r.tipo(), r.unita(), r.residuoAnnoPrecedente(), r.maturato(),
                    r.goduto(), r.saldo().add(BigDecimal.TEN)));
            return x;
        });

        assertThat(validatore.verifica(errato).avvisi()).containsExactlyInAnyOrder(
                "Cedolino di tredicesima senza voce di tredicesima",
                "Saldo del rateo FERIE (GIORNI) diverso da residuo + maturato - goduto");
    }

    @Test
    void senzaConfidenzaDelModelloSiPartedaUno() throws IOException {
        var c = base("rossi-beta-2024-10");
        var p = c.periodo();
        var errato = con(c, x -> {
            x.confidenza = null;
            x.periodo = new CedolinoEstratto.Periodo(null, p.mese(), p.tipo(), p.dataElaborazione(), null, null);
            return x;
        });

        var verifica = validatore.verifica(errato);

        assertThat(verifica.avvisi()).containsExactly("Anno del periodo di retribuzione non determinato");
        assertThat(verifica.cedolino().confidenza().complessiva()).isEqualByComparingTo("0.8");
        assertThat(verifica.cedolino().confidenza().campi()).singleElement()
                .satisfies(cc -> assertThat(cc.campo()).isEqualTo("periodo.anno"));
    }
}
