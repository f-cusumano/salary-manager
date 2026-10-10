package com.fcusumano.sam.cedolini.application;

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
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Cedolini inseriti a mano di prova, con dati inventati coerenti con lo scenario delle fixture (Mario Rossi, Alfa
 * Software: maggio 2023 e' la mensilita' mancante) e metodi per variarne una parte.
 */
final class CedoliniManualiDiProva {

    static final String CF_MARIO = "RSSMRA80A01H501U";
    /** Persona non presente nelle fixture (codice fiscale inventato con il carattere di controllo corretto). */
    static final String CF_GIOVANNA = "BNCGNN90A41F205C";
    static final String PIVA_ALFA = "01234567897";
    /** Azienda non presente nelle fixture (partita IVA inventata con la cifra di controllo corretta). */
    static final String PIVA_GAMMA = "09876543217";

    private CedoliniManualiDiProva() {
    }

    /** Maggio 2023 di Mario Rossi in Alfa Software con tutti i campi valorizzati e controlli di coerenza superati. */
    static CedolinoManuale completo() {
        return new CedolinoManuale(
                new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi", LocalDate.of(1980, 1, 1), "000123"),
                new CedolinoManuale.DatoreLavoro(PIVA_ALFA, PIVA_ALFA, "1122334455", "98765432/10",
                        "Alfa Software S.r.l.", "Via Roma 1, 00100 Roma (RM)", "ALFA01"),
                new CedolinoManuale.DatiContrattuali(LocalDate.of(2021, 9, 1), null, "Impiegato - Programmatore", "5",
                        "CCNL Metalmeccanica Industria", "C011"),
                new CedolinoManuale.Periodo(2023, 5, TipoMensilita.ORDINARIA, LocalDate.of(2023, 5, 26),
                        YearMonth.of(2023, 4)),
                new CedolinoManuale.Totali(n("2100.00"), n("2100.00"), n("450.00"), n("0.40"), n("1650.40")),
                new ElementiRetributivi(n("1800.00"), null, n("300.00"), true, null, null, null, n("2100.00"),
                        n("12.13873"), n("80.76923"), n("173"), n("26")),
                List.of(
                        new Voce("7020", "Retribuzione ordinaria", CategoriaVoce.COMPETENZA, n("26.00"), "giorni",
                                n("80.76923"), n("2100.00"), null),
                        new Voce("7000", "Contributo IVS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE, n("9.19"), "%",
                                n("2100.00"), null, n("192.99")),
                        new Voce("8000", "Ritenuta IRPEF", CategoriaVoce.TRATTENUTA_FISCALE, null, null, null, null,
                                n("257.01")),
                        new Voce(null, "Ferie godute", CategoriaVoce.PRESENZA, n("1.00"), "giorni", null, null, null)),
                List.of(
                        new Rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, n("5.00"), n("2.16667"), n("1.00"),
                                n("6.16667")),
                        new Rateo(TipoRateo.ROL, UnitaMisura.ORE, n("10.00"), n("6.00"), n("0"), n("16.00"))),
                new DatiTfr(n("5000.00"), n("50.00"), n("8.50"), n("700.00"), n("155.56"), null, null, null,
                        n("2100.00"), null, null),
                new Presenze(n("21.00"), n("168.00"), n("26.00")),
                new CedolinoManuale.Scatti(1, LocalDate.of(2025, 9, 1)),
                new BuoniPasto(true, n("20"), n("8.00"), n("160.00")),
                new ProgressiviAnnui(n("10500.00"), n("9500.00"), n("2000.00")),
                false,
                false);
    }

    /** Il minimo indispensabile per maggio 2023 di Mario Rossi in Alfa Software (totali coerenti). */
    static CedolinoManuale essenziale() {
        return new CedolinoManuale(
                new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi", null, null),
                new CedolinoManuale.DatoreLavoro(null, PIVA_ALFA, null, null, "Alfa Software S.r.l.", null, null),
                null,
                new CedolinoManuale.Periodo(2023, 5, TipoMensilita.ORDINARIA, null, null),
                new CedolinoManuale.Totali(null, n("2100.00"), n("450.00"), null, n("1650.00")),
                null,
                List.of(new Voce(null, "Retribuzione ordinaria", CategoriaVoce.COMPETENZA, null, null, null,
                        n("2100.00"), null)),
                List.of(),
                null, null, null, null, null, false, false);
    }

    /** Nessun campo valorizzato (non acquisibile: per i controlli di forma). */
    static CedolinoManuale vuoto() {
        return new CedolinoManuale(null, null, null, null, null, null, null, null, null, null, null, null, null,
                false, false);
    }

    static BigDecimal n(String valore) {
        return new BigDecimal(valore);
    }

    // ------------------------------------------------------------------------------------------ varianti

    static CedolinoManuale conDipendente(CedolinoManuale c, CedolinoManuale.Dipendente d) {
        return new CedolinoManuale(d, c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conDatore(CedolinoManuale c, CedolinoManuale.DatoreLavoro a) {
        return new CedolinoManuale(c.dipendente(), a, c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conContratto(CedolinoManuale c, CedolinoManuale.DatiContrattuali k) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), k, c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conPeriodo(CedolinoManuale c, CedolinoManuale.Periodo p) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), p, c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conTotali(CedolinoManuale c, CedolinoManuale.Totali t) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), t,
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conRetribuzione(CedolinoManuale c, ElementiRetributivi r) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                r, c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conVoci(CedolinoManuale c, List<Voce> voci) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), voci, c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conRatei(CedolinoManuale c, List<Rateo> ratei) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), ratei, c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conTfr(CedolinoManuale c, DatiTfr tfr) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), tfr, c.presenze(), c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conScatti(CedolinoManuale c, CedolinoManuale.Scatti s) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), s, c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conBuoniPasto(CedolinoManuale c, BuoniPasto b) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), b,
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conProgressivi(CedolinoManuale c, ProgressiviAnnui g) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(), c.buoniPasto(),
                g, c.contieneTredicesima(), c.contieneQuattordicesima());
    }

    static CedolinoManuale conPresenze(CedolinoManuale c, Presenze p) {
        return new CedolinoManuale(c.dipendente(), c.datoreLavoro(), c.datiContrattuali(), c.periodo(), c.totali(),
                c.retribuzione(), c.voci(), c.ratei(), c.tfr(), p, c.scatti(), c.buoniPasto(),
                c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima());
    }
}
