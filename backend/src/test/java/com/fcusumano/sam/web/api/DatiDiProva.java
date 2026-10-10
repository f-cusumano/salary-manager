package com.fcusumano.sam.web.api;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.FontePeriodo;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroDettaglio;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.AttribuzioneCaricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Dati inventati per i test dello strato REST (nessun dato reale).
 */
final class DatiDiProva {

    static final String CF_ROSSI = "RSSMRA80A01H501U";
    static final OffsetDateTime ISTANTE = OffsetDateTime.of(2025, 3, 10, 9, 30, 0, 0, ZoneOffset.UTC);
    static final Ccnl COMMERCIO = new Ccnl("COMMERCIO", "Commercio e terziario", 14, 36, 10, 7, null);
    static final Ccnl METALMECCANICO = new Ccnl("METALMECCANICO_INDUSTRIA", "Metalmeccanico industria", 13, 24, 5,
            null, null);

    private DatiDiProva() {
    }

    static Persona persona() {
        return new Persona(new PersonaId(1), CF_ROSSI, "Mario", "Rossi", LocalDate.of(1980, 1, 1));
    }

    static PersonaSintesi personaSintesi(String etichetta) {
        return new PersonaSintesi(new PersonaId(1), "Mario", "Rossi", CF_ROSSI, etichetta);
    }

    static Azienda azienda() {
        return new Azienda(new AziendaId(3), null, "01234567897", "1122334455", "Alfa Software S.r.l.",
                "Via Roma 1, Milano", "A01");
    }

    static PeriodoRapporto periodoAttuale() {
        return new PeriodoRapporto(YearMonth.of(2023, 1), null, true, FontePeriodo.DATA_CONTRATTUALE,
                FontePeriodo.NON_DISPONIBILE);
    }

    static PeriodoRapporto periodoConcluso() {
        return new PeriodoRapporto(YearMonth.of(2023, 1), YearMonth.of(2024, 6), false,
                FontePeriodo.DATA_CONTRATTUALE, FontePeriodo.DATA_CONTRATTUALE);
    }

    static RapportoLavoro rapporto(String ccnlCodice, String note) {
        return new RapportoLavoro(new RapportoLavoroId(7), new PersonaId(1), new AziendaId(3),
                LocalDate.of(2023, 1, 9), null, "Impiegato", "3", "D001", ccnlCodice, false, note,
                YearMonth.of(2023, 1), YearMonth.of(2024, 7));
    }

    static RapportoLavoroDettaglio dettaglio(String ccnlCodice, PeriodoRapporto periodo) {
        return new RapportoLavoroDettaglio(rapporto(ccnlCodice, "Promemoria"), persona(), azienda(),
                "Alfa Software S.r.l.", periodo);
    }

    static CedolinoSintesi sintesi(long id, Competenza competenza, String netto, Rateo... ratei) {
        var periodo = switch (competenza) {
            case Competenza.Mensilita m when m.mese() <= 12 -> m.comeYearMonth();
            case Competenza.Mensilita m -> YearMonth.of(m.anno(), 12);
            case Competenza.FineRapporto f -> f.periodoRetribuzione();
        };
        return new CedolinoSintesi(new CedolinoId(id), new RapportoLavoroId(7), competenza, periodo,
                periodo.atEndOfMonth(), new BigDecimal(netto).multiply(new BigDecimal("1.3")), new BigDecimal(netto),
                List.of(ratei), competenza.anno() + "." + competenza.codice() + "_3_1.pdf");
    }

    /** Lotto dei file in errore di prova. */
    static final UUID LOTTO_IN_ERRORE = UUID.fromString("7d1e2f3a-4b5c-4d6e-8f90-a1b2c3d4e5f6");

    /**
     * File in errore di prova del rapporto 7.
     *
     * @param competenza mensilita' associata, null se non associato
     */
    static CaricamentoInErrore fileInErrore(long id, String nome, MotivoErrore motivo, Competenza.Mensilita competenza,
                                            boolean competenzaPresente) {
        var attribuzione = competenza == null ? null
                : new AttribuzioneCaricamento(new RapportoLavoroId(7), competenza, competenzaPresente);
        return new CaricamentoInErrore(new CaricamentoId(id), LOTTO_IN_ERRORE, nome, 2048, motivo,
                "Il lettore non riesce a leggere questo file", motivo != null && motivo.conservaPdf(), false,
                attribuzione, ISTANTE);
    }

    static Rateo ferie(String saldo) {
        return new Rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, null, null, null,
                saldo == null ? null : new BigDecimal(saldo));
    }

    static RiepilogoRapporto riepilogo(List<RiepilogoRapporto.IndicatoriMensilita> indicatori) {
        return new RiepilogoRapporto(
                new RapportoLavoroId(7),
                ISTANTE,
                new RiepilogoRapporto.Anzianita(YearMonth.of(2023, 1), YearMonth.of(2025, 2), true, 2, 2,
                        FonteDato.DATA_CONTRATTUALE, FonteDato.CEDOLINI),
                new RiepilogoRapporto.Scatti(1, 10, LocalDate.of(2026, 2, 1), FonteDato.CEDOLINI, FonteDato.CEDOLINI,
                        false),
                new RiepilogoRapporto.Retribuzione(new BigDecimal("32900.00000"), new BigDecimal("2350.00000"), 14,
                        new BigDecimal("2000.00000"), null, new BigDecimal("300.12345"), new BigDecimal("50.00000"),
                        null, null, false, null, new CedolinoId(12)),
                true,
                new RiepilogoRapporto.Ratei(new CedolinoId(12), List.of(new RiepilogoRapporto.SaldoRateo(
                        TipoRateo.FERIE, UnitaMisura.GIORNI, new BigDecimal("12.50000"), new BigDecimal("1129.81000"),
                        "Paga giornaliera stampata"))),
                new RiepilogoRapporto.Tfr(new BigDecimal("4500.00000"), new BigDecimal("180.00000"),
                        new BigDecimal("4500.00000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00000"),
                        new BigDecimal("3465.00000"), new BigDecimal("0.23000"), false, YearMonth.of(2025, 2), null,
                        false, false, null, null),
                indicatori);
    }
}
