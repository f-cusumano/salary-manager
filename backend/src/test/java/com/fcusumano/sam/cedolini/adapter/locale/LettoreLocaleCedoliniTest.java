package com.fcusumano.sam.cedolini.adapter.locale;

import static com.fcusumano.sam.cedolini.adapter.locale.CedoliniSintetici.CODICE_FISCALE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.adapter.locale.CedoliniSintetici.Job;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.RateoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.VoceEstratta;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Lettore locale sui cedolini sintetici di {@link CedoliniSintetici} (dati inventati): campo per campo per ogni layout,
 * tipi particolari (tredicesima, fine rapporto, cambio di azienda a meta' mese) e documenti che il lettore deve
 * rifiutare (scansione, layout sconosciuto, dati essenziali mancanti, file non PDF).
 */
class LettoreLocaleCedoliniTest {

    private final LettoreLocaleCedolini lettore =
            new LettoreLocaleCedolini(Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC));

    // ------------------------------------------------------------------------------------------------ JOB Sistemi

    @Test
    void jobSistemiOrdinariaCampoPerCampo() {
        var risultato = lettore.estrai(CedoliniSintetici.jobSistemi(Job.ordinaria()));
        var c = risultato.cedolino();

        assertThat(risultato.modello()).isEqualTo("lettore-locale-1/job-sistemi");
        assertThat(risultato.avvisi()).isEmpty();
        assertThat(c.numeroCedoliniNelDocumento()).isEqualTo(1);
        assertThat(c.dipendente().codiceFiscale()).isEqualTo(CODICE_FISCALE);
        assertThat(c.dipendente().cognome()).isEqualTo("ROSSI");
        assertThat(c.dipendente().nome()).isEqualTo("MARIO");
        assertThat(c.dipendente().dataNascita()).isEqualTo("1980-01-01");
        assertThat(c.dipendente().codiceDipendente()).isEqualTo("1234");
        assertThat(c.datoreLavoro().ragioneSociale()).isEqualTo("ALFA SOFTWARE S.R.L.");
        assertThat(c.datoreLavoro().indirizzo()).isEqualTo("VIA DELLE ROSE, 10, 00100 ROMA (RM)");
        assertThat(c.datoreLavoro().codiceFiscale()).isEqualTo("01234567897");
        assertThat(c.datoreLavoro().partitaIva()).isEqualTo("01234567897");
        assertThat(c.datoreLavoro().posizioneInps()).isEqualTo("1234567890");
        assertThat(c.datoreLavoro().codiceAzienda()).isEqualTo("1234");
        assertThat(c.datiContrattuali().dataAssunzione()).isEqualTo("2015-09-01");
        assertThat(c.datiContrattuali().dataCessazione()).isNull();
        assertThat(c.datiContrattuali().qualifica()).isEqualTo("IMPIEGATO");
        assertThat(c.datiContrattuali().livello()).isEqualTo("4");
        assertThat(c.datiContrattuali().ccnl()).isEqualTo("COMMERCIO TERZIARIO (CNEL H011)");
        assertThat(c.periodo().anno()).isEqualTo(2024);
        assertThat(c.periodo().mese()).isEqualTo(4);
        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(c.periodo().dataElaborazione()).isEqualTo("2024-05-05");
        assertThat(c.periodo().annoVariabili()).isEqualTo(2024);
        assertThat(c.periodo().meseVariabili()).isEqualTo(3);
        assertImporto(c.totali().totaleCompetenze(), "2045.50");
        assertImporto(c.totali().lordo(), "2045.50");
        assertImporto(c.totali().totaleTrattenute(), "511.14");
        assertImporto(c.totali().arrotondamento(), "-0.36");
        assertImporto(c.totali().netto(), "1534.00");
        assertImporto(c.retribuzione().minimo(), "1800");
        assertImporto(c.retribuzione().superminimo(), "200");
        assertImporto(c.retribuzione().pagaOraria(), "11.56069");
        assertImporto(c.retribuzione().pagaGiornaliera(), "76.92308");
        assertImporto(c.retribuzione().totale(), "2000");
        assertThat(c.voci()).extracting(VoceEstratta::descrizione, VoceEstratta::categoria).containsExactly(
                tuple("Retribuzione ordinaria", CategoriaVoce.COMPETENZA),
                tuple("Festivita' goduta", CategoriaVoce.PRESENZA),
                tuple("Ferie godute", CategoriaVoce.PRESENZA),
                tuple("Rimborso spese km", CategoriaVoce.NON_RETRIBUTIVA),
                tuple("Buoni pasto", CategoriaVoce.INFORMATIVA),
                tuple("Quota sindacale", CategoriaVoce.ALTRA_TRATTENUTA),
                tuple("INPS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE),
                tuple("FONDO SANITARIO", CategoriaVoce.INFORMATIVA),
                tuple("IRPEF + IMP. SOST.", CategoriaVoce.TRATTENUTA_FISCALE));
        var ordinaria = c.voci().getFirst();
        assertThat(ordinaria.codice()).isEqualTo("1");
        assertThat(ordinaria.unita()).isEqualTo("giorni");
        assertImporto(ordinaria.quantita(), "26");
        assertImporto(ordinaria.base(), "76.92308");
        assertImporto(ordinaria.importoCompetenza(), "2000");
        assertThat(ordinaria.importoTrattenuta()).isNull();
        var inps = c.voci().get(6);
        assertImporto(inps.quantita(), "9.19");
        assertThat(inps.unita()).isEqualTo("%");
        assertImporto(inps.base(), "2000");
        assertImporto(inps.importoTrattenuta(), "183.80");
        assertThat(c.ratei()).extracting(RateoEstratto::tipo, RateoEstratto::unita).containsExactly(
                tuple(TipoRateo.FERIE, UnitaMisura.GIORNI),
                tuple(TipoRateo.ROL, UnitaMisura.ORE),
                tuple(TipoRateo.EX_FESTIVITA, UnitaMisura.ORE));
        assertRateo(c.ratei().get(0), "12", "2.17", "1", "13.17");
        assertRateo(c.ratei().get(1), "40", "6", "8", "38");
        assertRateo(c.ratei().get(2), "8", "2.67", "0", "10.67");
        assertImporto(c.tfr().fondo31DicembreAnnoPrecedente(), "5000");
        assertImporto(c.tfr().quotaAnno(), "552.60");
        assertImporto(c.tfr().quotaMese(), "138.15");
        assertImporto(c.tfr().retribuzioneUtile(), "2000");
        assertImporto(c.presenze().giorniLavorati(), "21");
        assertImporto(c.presenze().oreLavorate(), "168");
        assertImporto(c.presenze().giorniRetribuiti(), "26");
        assertThat(c.scatti().numeroScatti()).isEqualTo(2);
        assertThat(c.scatti().dataProssimoScatto()).isEqualTo("2025-10-01");
        assertImporto(c.buoniPasto().numero(), "20");
        assertImporto(c.buoniPasto().valoreUnitario(), "8");
        assertImporto(c.buoniPasto().importo(), "160");
        assertImporto(c.progressivi().imponibilePrevidenziale(), "8000");
        assertImporto(c.progressivi().imponibileIrpef(), "7264.80");
        assertThat(c.progressivi().irpefTrattenuta()).isNull();
        assertThat(c.campiNonTrovati()).isEmpty();
        assertThat(c.confidenza().complessiva()).isEqualByComparingTo("0.95");
    }

    @Test
    void nonEstraeMaiIDatiBancari() {
        var risultato = lettore.estrai(CedoliniSintetici.jobSistemi(Job.ordinaria()));

        assertThat(risultato.jsonOriginale()).doesNotContain("IT60", "X054", "0123 456", "BONIFICO");
        assertThat(risultato.cedolino().toString()).doesNotContain("IT60", "X054", "0123 456");
    }

    @Test
    void jobSistemiTredicesimaConIntestazioneSenzaMese() {
        var risultato = lettore.estrai(CedoliniSintetici.jobSistemi(Job.tredicesima()));
        var c = risultato.cedolino();

        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.TREDICESIMA);
        assertThat(c.periodo().anno()).isEqualTo(2024);
        assertThat(c.contieneTredicesima()).isTrue();
        assertThat(risultato.avvisi()).anyMatch(a -> a.contains("tredicesima"));
        assertImporto(c.totali().netto(), "1396.40");
        assertImporto(c.totali().arrotondamento(), "0.20");
        assertImporto(c.progressivi().irpefTrattenuta(), "6120");
        assertThat(c.voci()).extracting(VoceEstratta::descrizione)
                .containsExactly("Tredicesima mensilita'", "INPS", "FONDO SANITARIO", "IRPEF + IMP. SOST.");
    }

    @Test
    void jobSistemiFineRapportoDopoLaCessazione() {
        var risultato = lettore.estrai(CedoliniSintetici.jobSistemi(Job.fineRapporto()));
        var c = risultato.cedolino();

        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.FINE_RAPPORTO);
        assertThat(c.datiContrattuali().dataCessazione()).isEqualTo("2024-03-31");
        assertThat(risultato.avvisi()).anyMatch(a -> a.contains("fine rapporto"));
        assertImporto(c.totali().netto(), "1075.00");
    }

    // -------------------------------------------------------------------------------------------------- Zucchetti

    @Test
    void zucchettiCampoPerCampo() {
        var risultato = lettore.estrai(CedoliniSintetici.zucchetti(false));
        var c = risultato.cedolino();

        assertThat(risultato.modello()).isEqualTo("lettore-locale-1/zucchetti");
        assertZucchetti(c);
    }

    @Test
    void zucchettiGraficaRecente() {
        var risultato = lettore.estrai(CedoliniSintetici.zucchetti(true));

        assertThat(risultato.modello()).isEqualTo("lettore-locale-1/zucchetti");
        assertZucchetti(risultato.cedolino());
    }

    private static void assertZucchetti(CedolinoEstratto c) {
        assertThat(c.dipendente().codiceFiscale()).isEqualTo(CODICE_FISCALE);
        assertThat(c.dipendente().cognome()).isEqualTo("ROSSI");
        assertThat(c.dipendente().nome()).isEqualTo("MARIO");
        assertThat(c.dipendente().dataNascita()).isEqualTo("1980-01-01");
        assertThat(c.dipendente().codiceDipendente()).isEqualTo("00000001234");
        assertThat(c.datoreLavoro().codiceAzienda()).isEqualTo("0000000123");
        assertThat(c.datoreLavoro().ragioneSociale()).isEqualTo("ALFA SOFTWARE S.R.L.");
        assertThat(c.datoreLavoro().indirizzo()).isEqualTo("VIA DELLE ROSE, 10, 00100 ROMA (RM)");
        assertThat(c.datoreLavoro().codiceFiscale()).isEqualTo("01234567897");
        assertThat(c.datoreLavoro().posizioneInps()).isEqualTo("1234567890/00");
        assertThat(c.datiContrattuali().dataAssunzione()).isEqualTo("2020-03-16");
        assertThat(c.datiContrattuali().qualifica()).isEqualTo("Impiegato");
        assertThat(c.datiContrattuali().livello()).isEqualTo("4");
        assertThat(c.periodo().anno()).isEqualTo(2020);
        assertThat(c.periodo().mese()).isEqualTo(3);
        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(c.periodo().dataElaborazione()).isEqualTo("2020-04-05");
        assertThat(c.periodo().meseVariabili()).isEqualTo(2);
        assertImporto(c.totali().totaleCompetenze(), "953.08");
        assertImporto(c.totali().totaleTrattenute(), "114.53");
        assertImporto(c.totali().arrotondamento(), "0.45");
        assertImporto(c.totali().netto(), "839.00");
        assertImporto(c.retribuzione().minimo(), "1800");
        assertImporto(c.retribuzione().superminimo(), "200");
        assertThat(c.retribuzione().superminimoAssorbibile()).isTrue();
        assertImporto(c.retribuzione().totale(), "2000");
        assertThat(c.scatti().dataProssimoScatto()).isEqualTo("2025-09-01");
        assertImporto(c.presenze().giorniRetribuiti(), "12");
        assertImporto(c.presenze().giorniLavorati(), "11");
        assertImporto(c.presenze().oreLavorate(), "88");
        assertThat(c.voci()).extracting(VoceEstratta::codice, VoceEstratta::categoria).containsExactly(
                tuple("1001", CategoriaVoce.COMPETENZA),
                tuple("2050", CategoriaVoce.NON_RETRIBUTIVA),
                tuple("9001", CategoriaVoce.TRATTENUTA_PREVIDENZIALE),
                tuple("9101", CategoriaVoce.INFORMATIVA),
                tuple("9102", CategoriaVoce.INFORMATIVA),
                tuple("9110", CategoriaVoce.TRATTENUTA_FISCALE),
                tuple("9500", CategoriaVoce.TRATTENUTA_FISCALE),
                tuple(null, CategoriaVoce.INFORMATIVA),
                tuple(null, CategoriaVoce.INFORMATIVA));
        var ordinaria = c.voci().getFirst();
        assertThat(ordinaria.unita()).isEqualTo("giorni");
        assertImporto(ordinaria.quantita(), "12");
        assertImporto(ordinaria.base(), "76.92308");
        assertImporto(ordinaria.importoCompetenza(), "923.08");
        assertThat(c.voci().get(1).unita()).isEqualTo("km");
        assertThat(c.voci().get(2).unita()).isEqualTo("%");
        assertImporto(c.voci().get(2).quantita(), "9.19");
        // trattenuta negativa stampata nella colonna trattenute: importo positivo tra le competenze
        assertImporto(c.voci().get(6).importoCompetenza(), "50");
        assertThat(c.voci().get(6).importoTrattenuta()).isNull();
        assertThat(c.ratei()).extracting(RateoEstratto::tipo, RateoEstratto::unita).containsExactly(
                tuple(TipoRateo.FERIE, UnitaMisura.GIORNI),
                tuple(TipoRateo.PERMESSI, UnitaMisura.ORE));
        assertRateo(c.ratei().get(0), "0", "1.08333", "0", "1.08333");
        assertRateo(c.ratei().get(1), "0", "4.33333", "0", "4.33333");
        assertImporto(c.tfr().fondo31DicembreAnnoPrecedente(), "0");
        assertImporto(c.tfr().quotaAnno(), "70.60");
        assertImporto(c.tfr().quotaMese(), "70.60");
        assertImporto(c.tfr().retribuzioneUtile(), "953.08");
        assertImporto(c.progressivi().imponibilePrevidenziale(), "923.08");
        assertImporto(c.progressivi().imponibileIrpef(), "838.25");
        assertImporto(c.progressivi().irpefTrattenuta(), "79.70");
    }

    // ------------------------------------------------------------------------------------------- modulo M39320

    @Test
    void moduloM39320CampoPerCampo() {
        var risultato = lettore.estrai(CedoliniSintetici.moduloM39320(false));
        var c = risultato.cedolino();

        assertThat(risultato.modello()).isEqualTo("lettore-locale-1/modulo-m39320");
        assertThat(c.dipendente().codiceFiscale()).isEqualTo(CODICE_FISCALE);
        assertThat(c.dipendente().cognome()).isEqualTo("ROSSI");
        assertThat(c.dipendente().nome()).isEqualTo("MARIO");
        assertThat(c.dipendente().dataNascita()).isEqualTo("1980-01-01");
        assertThat(c.dipendente().codiceDipendente()).isEqualTo("57");
        assertThat(c.datoreLavoro().codiceAzienda()).isEqualTo("BT0042");
        assertThat(c.datoreLavoro().ragioneSociale()).isEqualTo("BETA SERVIZI S.R.L.");
        assertThat(c.datoreLavoro().posizioneInps()).isEqualTo("9876543210");
        assertThat(c.datiContrattuali().dataAssunzione()).isEqualTo("2016-02-01");
        assertThat(c.datiContrattuali().dataCessazione()).isEqualTo("2020-03-15");
        assertThat(c.datiContrattuali().qualifica()).isEqualTo("IMPIEGATO - PROGRAMMATORE");
        assertThat(c.datiContrattuali().livello()).isEqualTo("4");
        assertThat(c.periodo().anno()).isEqualTo(2020);
        assertThat(c.periodo().mese()).isEqualTo(3);
        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(c.periodo().dataElaborazione()).isEqualTo("2020-03-31");
        assertThat(c.periodo().meseVariabili()).isEqualTo(2);
        assertImporto(c.totali().totaleCompetenze(), "1028.92");
        assertImporto(c.totali().totaleTrattenute(), "222.48");
        assertImporto(c.totali().arrotondamento(), "0.56");
        assertImporto(c.totali().netto(), "807.00");
        assertImporto(c.retribuzione().retribuzioneBase(), "1800");
        assertImporto(c.retribuzione().pagaOraria(), "10.40462");
        assertImporto(c.retribuzione().pagaGiornaliera(), "69.23077");
        assertImporto(c.retribuzione().totale(), "1800");
        assertImporto(c.presenze().oreLavorate(), "84");
        assertImporto(c.presenze().giorniLavorati(), "11");
        assertImporto(c.presenze().giorniRetribuiti(), "15");
        assertThat(c.scatti().dataProssimoScatto()).isEqualTo("2022-02-01");
        assertThat(c.voci()).extracting(VoceEstratta::descrizione, VoceEstratta::categoria).containsExactly(
                tuple("RETRIBUZIONE ORDINARIA", CategoriaVoce.COMPETENZA),
                tuple("ORE STRAORDINARIE", CategoriaVoce.COMPETENZA),
                tuple("FERIE GODUTE", CategoriaVoce.PRESENZA),
                tuple("IMP.PREV. MESE", CategoriaVoce.INFORMATIVA),
                tuple("CONTR.IVS", CategoriaVoce.TRATTENUTA_PREVIDENZIALE),
                tuple("BUONI PASTO", CategoriaVoce.NON_RETRIBUTIVA),
                tuple("INAIL Pos: 87654321/12", CategoriaVoce.INFORMATIVA),
                tuple("IMPONIBILE FISCALE", CategoriaVoce.INFORMATIVA),
                tuple("IRPEF LORDA", CategoriaVoce.INFORMATIVA),
                tuple("RITENUTE IRPEF", CategoriaVoce.TRATTENUTA_FISCALE),
                tuple("ADD.REG. ACCONTO", CategoriaVoce.TRATTENUTA_FISCALE));
        assertImporto(c.voci().get(4).quantita(), "9.19");
        assertThat(c.voci().get(4).unita()).isEqualTo("%");
        assertImporto(c.voci().get(6).base(), "951.92");
        assertImporto(c.buoniPasto().numero(), "11");
        assertImporto(c.buoniPasto().valoreUnitario(), "7");
        assertImporto(c.buoniPasto().importo(), "77");
        assertThat(c.ratei()).extracting(RateoEstratto::tipo, RateoEstratto::unita).containsExactly(
                tuple(TipoRateo.FERIE, UnitaMisura.GIORNI),
                tuple(TipoRateo.EX_FESTIVITA, UnitaMisura.ORE),
                tuple(TipoRateo.PERMESSI, UnitaMisura.ORE));
        assertRateo(c.ratei().get(0), "5", "6.5", "7", "4.5");
        assertRateo(c.ratei().get(1), "8", "8", "8", "8");
        assertRateo(c.ratei().get(2), "20", "18", "18", "20");
        assertImporto(c.tfr().fondo31DicembreAnnoPrecedente(), "4500");
        assertImporto(c.tfr().retribuzioneUtile(), "951.92");
        assertImporto(c.progressivi().imponibileIrpef(), "2598.32");
        assertImporto(c.progressivi().irpefTrattenuta(), "360");
    }

    @Test
    void moduloM39320FineRapportoConTfrLiquidato() {
        var risultato = lettore.estrai(CedoliniSintetici.moduloM39320(true));
        var c = risultato.cedolino();

        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.FINE_RAPPORTO);
        assertThat(c.periodo().anno()).isEqualTo(2020);
        assertThat(risultato.avvisi()).anyMatch(a -> a.contains("fine rapporto"));
        assertImporto(c.totali().netto(), "3657.00");
        assertImporto(c.tfr().fondo31DicembreAnnoPrecedente(), "4500");
        assertImporto(c.tfr().rivalutazione(), "45");
        assertImporto(c.tfr().impostaSostitutiva(), "7.65");
        assertImporto(c.tfr().quotaAnno(), "211.48");
        assertImporto(c.tfr().liquidatoLordo(), "4748.83");
        assertImporto(c.tfr().ritenuta(), "1092.23");
    }

    @Test
    void cambioDiAziendaAMetaMese() {
        var uscita = lettore.estrai(CedoliniSintetici.moduloM39320(false)).cedolino();
        var entrata = lettore.estrai(CedoliniSintetici.zucchetti(false)).cedolino();

        // stesso dipendente e stesso mese, due rapporti: entrambi ordinari (nessun fine rapporto per chi esce)
        assertThat(uscita.dipendente().codiceFiscale()).isEqualTo(entrata.dipendente().codiceFiscale());
        assertThat(uscita.periodo().anno()).isEqualTo(entrata.periodo().anno());
        assertThat(uscita.periodo().mese()).isEqualTo(entrata.periodo().mese());
        assertThat(uscita.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(entrata.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(uscita.datiContrattuali().dataCessazione()).isEqualTo("2020-03-15");
        assertThat(entrata.datiContrattuali().dataAssunzione()).isEqualTo("2020-03-16");
        assertThat(uscita.datoreLavoro().ragioneSociale()).isNotEqualTo(entrata.datoreLavoro().ragioneSociale());
        assertThat(uscita.datoreLavoro().posizioneInps()).isNotEqualTo(entrata.datoreLavoro().posizioneInps());
    }

    // ------------------------------------------------------------------------------------------------- rifiuti

    @Test
    void unaScansioneNonSiLeggeESuggerisceClaude() {
        assertThatThrownBy(() -> lettore.estrai(CedoliniSintetici.scansione()))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.PDF_SENZA_TESTO))
                .hasMessageContaining("scansione")
                .hasMessageContaining("SAM_LETTORE=claude");
    }

    @Test
    void unLayoutSconosciutoNonSiLeggeESuggerisceClaude() {
        assertThatThrownBy(() -> lettore.estrai(CedoliniSintetici.layoutSconosciuto()))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.LAYOUT_NON_RICONOSCIUTO))
                .hasMessageContaining("JOB Sistemi")
                .hasMessageContaining("Zucchetti")
                .hasMessageContaining("SAM_LETTORE=claude")
                .hasMessageNotContaining(CODICE_FISCALE);
    }

    @Test
    void senzaIDatiEssenzialiIlLayoutRiconosciutoNonBasta() {
        assertThatThrownBy(() -> lettore.estrai(CedoliniSintetici.jobSistemi(Job.ordinaria().senzaNetto())))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.LAYOUT_NON_RICONOSCIUTO))
                .hasMessageContaining("JOB Sistemi")
                .hasMessageContaining("netto");
    }

    @Test
    void unFileCheNonEUnPdfEUnDocumentoNonValido() {
        assertThatThrownBy(() -> lettore.estrai("non sono un pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));
        assertThatThrownBy(() -> lettore.estrai(new byte[0]))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));
    }

    // -------------------------------------------------------------------------------------------------- utilita'

    private static void assertImporto(BigDecimal valore, String atteso) {
        assertThat(valore).isNotNull().isEqualByComparingTo(atteso);
    }

    private static void assertRateo(RateoEstratto r, String residuo, String maturato, String goduto, String saldo) {
        assertImporto(r.residuoAnnoPrecedente(), residuo);
        assertImporto(r.maturato(), maturato);
        assertImporto(r.goduto(), goduto);
        assertImporto(r.saldo(), saldo);
    }
}
