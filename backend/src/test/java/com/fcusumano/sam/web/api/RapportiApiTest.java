package com.fcusumano.sam.web.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.SegnalazioneId;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;

class RapportiApiTest extends ApiWebMvcTest {

    private static final RapportoLavoroId RAPPORTO = new RapportoLavoroId(7);

    @BeforeEach
    void cedoliniEMancanti() {
        when(anagrafica.elencoPersone()).thenReturn(List.of(DatiDiProva.personaSintesi("Mario Rossi")));
        when(catalogoCcnl.trova("COMMERCIO")).thenReturn(Optional.of(DatiDiProva.COMMERCIO));
        when(catalogoCcnl.trova("METALMECCANICO_INDUSTRIA")).thenReturn(Optional.of(DatiDiProva.METALMECCANICO));
        when(archivio.sintesiDiRapporto(RAPPORTO)).thenReturn(List.of(
                DatiDiProva.sintesi(14, Competenza.Mensilita.ordinaria(YearMonth.of(2023, 12)), "1800.00"),
                DatiDiProva.sintesi(30, new Competenza.FineRapporto(1, YearMonth.of(2024, 9)), "4200.00"),
                DatiDiProva.sintesi(16, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1)), "1810.00",
                        DatiDiProva.ferie("10.50000"), DatiDiProva.ferie(null)),
                DatiDiProva.sintesi(22, Competenza.Mensilita.quattordicesima(2024), "1900.00"),
                DatiDiProva.sintesi(20, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 6)), "1820.00"),
                DatiDiProva.sintesi(15, Competenza.Mensilita.tredicesima(2023), "1750.00"),
                DatiDiProva.sintesi(21, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 7)), "1830.00"),
                DatiDiProva.sintesi(31, new Competenza.FineRapporto(2, YearMonth.of(2024, 10)), "150.00"),
                DatiDiProva.sintesi(23, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 8)), "1840.00")));
        when(segnalazioni.mensilitaMancanti(RAPPORTO)).thenReturn(List.of(
                Competenza.Mensilita.ordinaria(YearMonth.of(2023, 11)),
                Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1)),
                Competenza.Mensilita.ordinaria(YearMonth.of(2024, 2))));
    }

    @Test
    void mensilitaInOrdineCronologicoInversoConTitoliPronti() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensilita[*].titolo").value(contains(
                        "Fine rapporto 2 – Ottobre 2024",
                        "Fine rapporto 1 – Settembre 2024",
                        "Agosto 2024",
                        "Quattordicesima 2024",
                        "Luglio 2024",
                        "Giugno 2024",
                        "Febbraio 2024",
                        "Gennaio 2024",
                        "Tredicesima 2023",
                        "Dicembre 2023",
                        "Novembre 2023")))
                .andExpect(jsonPath("$.mensilita[*].chiave").value(contains("2024.TFR02", "2024.TFR01", "2024.08",
                        "2024.14", "2024.07", "2024.06", "2024.02", "2024.01", "2023.13", "2023.12", "2023.11")))
                .andExpect(jsonPath("$.mensilita[*].stato").value(contains("PRESENTE", "PRESENTE", "PRESENTE",
                        "PRESENTE", "PRESENTE", "PRESENTE", "MANCANTE", "PRESENTE", "PRESENTE", "PRESENTE",
                        "MANCANTE")));
    }

    @Test
    void laQuattordicesimaSegueGiugnoSeIlCcnlNonIndicaIlMese() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio(null, DatiDiProva.periodoAttuale())));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensilita[2:6].titolo").value(contains(
                        "Agosto 2024", "Luglio 2024", "Quattordicesima 2024", "Giugno 2024")))
                .andExpect(jsonPath("$.ccnl").value(nullValue()))
                .andExpect(jsonPath("$.ccnlDaIndicare").value(true));
    }

    @Test
    void righePresentiEMancantiConIndicatoriERatei() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(calcoli.riepilogo(RAPPORTO)).thenReturn(Optional.of(DatiDiProva.riepilogo(List.of(
                new IndicatoriMensilita(new CedolinoId(23), true, false, null),
                new IndicatoriMensilita(new CedolinoId(21), false, true, null)))));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                // Agosto 2024: scatto in busta
                .andExpect(jsonPath("$.mensilita[2].cedolinoId").value(23))
                .andExpect(jsonPath("$.mensilita[2].scattoInBusta").value(true))
                .andExpect(jsonPath("$.mensilita[2].aumentoFuoriScatti").value(false))
                .andExpect(jsonPath("$.mensilita[2].netto").value(1840.0))
                .andExpect(jsonPath("$.mensilita[2].nomeFile").value("2024.08_3_1.pdf"))
                .andExpect(jsonPath("$.mensilita[2].periodoRetribuzione").value("2024-08"))
                .andExpect(jsonPath("$.mensilita[2].competenza.tipo").value("ORDINARIA"))
                .andExpect(jsonPath("$.mensilita[2].competenza.mese").value(8))
                .andExpect(jsonPath("$.mensilita[2].competenza.progressivoTfr").value(nullValue()))
                // Luglio 2024: aumento fuori scatti
                .andExpect(jsonPath("$.mensilita[4].aumentoFuoriScatti").value(true))
                .andExpect(jsonPath("$.mensilita[4].scattoInBusta").value(false))
                // Fine rapporto: progressivo, nessun mese
                .andExpect(jsonPath("$.mensilita[1].competenza.tipo").value("FINE_RAPPORTO"))
                .andExpect(jsonPath("$.mensilita[1].competenza.mese").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[1].competenza.progressivoTfr").value(1))
                .andExpect(jsonPath("$.mensilita[1].periodoRetribuzione").value("2024-09"))
                // Gennaio 2024: solo i ratei con saldo stampato
                .andExpect(jsonPath("$.mensilita[7].ratei", hasSize(1)))
                .andExpect(jsonPath("$.mensilita[7].ratei[0].tipo").value("FERIE"))
                .andExpect(jsonPath("$.mensilita[7].ratei[0].unita").value("GIORNI"))
                .andExpect(jsonPath("$.mensilita[7].ratei[0].saldo").value(10.5))
                // Febbraio 2024 mancante: solo titolo e periodo
                .andExpect(jsonPath("$.mensilita[6].stato").value("MANCANTE"))
                .andExpect(jsonPath("$.mensilita[6].periodoRetribuzione").value("2024-02"))
                .andExpect(jsonPath("$.mensilita[6].cedolinoId").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].netto").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].lordo").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].nomeFile").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].ratei", empty()))
                .andExpect(jsonPath("$.mensilita[6].scattoInBusta").value(false));
    }

    @Test
    void laMensilitaConUnFileInErroreAssociatoERossaMaUnCedolinoPrevale() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(caricamentiInErrore.associatiARapporto(RAPPORTO)).thenReturn(List.of(
                DatiDiProva.fileInErrore(41, "febbraio.pdf", MotivoErrore.PDF_PROTETTO,
                        Competenza.Mensilita.ordinaria(YearMonth.of(2024, 2)), false),
                DatiDiProva.fileInErrore(42, "gennaio.pdf", MotivoErrore.LETTURA_NON_RIUSCITA,
                        Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1)), true)));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensilita[*].chiave").value(contains("2024.TFR02", "2024.TFR01", "2024.08",
                        "2024.14", "2024.07", "2024.06", "2024.02", "2024.01", "2023.13", "2023.12", "2023.11")))
                .andExpect(jsonPath("$.mensilita[*].stato").value(contains("PRESENTE", "PRESENTE", "PRESENTE",
                        "PRESENTE", "PRESENTE", "PRESENTE", "IN_ERRORE", "PRESENTE", "PRESENTE", "PRESENTE",
                        "MANCANTE")))
                // Febbraio 2024: file in errore associato al posto della riga mancante
                .andExpect(jsonPath("$.mensilita[6].titolo").value("Febbraio 2024"))
                .andExpect(jsonPath("$.mensilita[6].periodoRetribuzione").value("2024-02"))
                .andExpect(jsonPath("$.mensilita[6].caricamentoId").value(41))
                .andExpect(jsonPath("$.mensilita[6].motivoErrore").value("PDF_PROTETTO"))
                .andExpect(jsonPath("$.mensilita[6].cedolinoId").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].netto").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].nomeFile").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[6].ratei", empty()))
                // Gennaio 2024: il cedolino prevale sul file associato
                .andExpect(jsonPath("$.mensilita[7].cedolinoId").value(16))
                .andExpect(jsonPath("$.mensilita[7].caricamentoId").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[7].motivoErrore").value(nullValue()))
                // Novembre 2023 resta mancante
                .andExpect(jsonPath("$.mensilita[10].caricamentoId").value(nullValue()))
                .andExpect(jsonPath("$.mensilita[10].motivoErrore").value(nullValue()));
    }

    @Test
    void dettaglioCompletoDelRapportoAttuale() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(anagrafica.elencoPersone()).thenReturn(List.of(
                DatiDiProva.personaSintesi("Mario Rossi (RSSMRA80A01H501U)")));
        when(calcoli.riepilogo(RAPPORTO)).thenReturn(Optional.of(DatiDiProva.riepilogo(List.of())));
        when(segnalazioni.segnalazioni(RAPPORTO)).thenReturn(List.of(new Segnalazione(new SegnalazioneId(40),
                RAPPORTO, null, Competenza.Mensilita.ordinaria(YearMonth.of(2024, 2)),
                TipoSegnalazione.MENSILITA_MANCANTE, Gravita.AVVISO, "Mensilità mancante: Febbraio 2024",
                "Non risulta caricato il cedolino di Febbraio 2024.",
                List.of(new Evidenza("Cedolino precedente", new CedolinoId(16),
                        Competenza.Mensilita.ordinaria(YearMonth.of(2024, 1)), "1.810,00 €")))));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"ral\":32900.00")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.persona.etichetta").value("Mario Rossi (RSSMRA80A01H501U)"))
                .andExpect(jsonPath("$.azienda.partitaIva").value("01234567897"))
                .andExpect(jsonPath("$.etichetta").value("Alfa Software S.r.l."))
                .andExpect(jsonPath("$.periodo.da").value("2023-01"))
                .andExpect(jsonPath("$.attuale").value(true))
                .andExpect(jsonPath("$.dataAssunzione").value("2023-01-09"))
                .andExpect(jsonPath("$.dataCessazione").value(nullValue()))
                .andExpect(jsonPath("$.qualifica").value("Impiegato"))
                .andExpect(jsonPath("$.livello").value("3"))
                .andExpect(jsonPath("$.ccnl.codice").value("COMMERCIO"))
                .andExpect(jsonPath("$.ccnl.meseQuattordicesima").value(7))
                .andExpect(jsonPath("$.ccnlDaIndicare").value(false))
                .andExpect(jsonPath("$.ccnlSceltoDaUtente").value(false))
                .andExpect(jsonPath("$.note").value("Promemoria"))
                .andExpect(jsonPath("$.segnalazioni[0].id").value(40))
                .andExpect(jsonPath("$.segnalazioni[0].tipo").value("MENSILITA_MANCANTE"))
                .andExpect(jsonPath("$.segnalazioni[0].gravita").value("AVVISO"))
                .andExpect(jsonPath("$.segnalazioni[0].cedolinoId").value(nullValue()))
                .andExpect(jsonPath("$.segnalazioni[0].competenza.chiave").value("2024.02"))
                .andExpect(jsonPath("$.segnalazioni[0].evidenze[0].cedolinoId").value(16))
                .andExpect(jsonPath("$.segnalazioni[0].evidenze[0].valore").value("1.810,00 €"))
                .andExpect(jsonPath("$.riepilogo.calcolatoIl").value("2025-03-10T09:30:00Z"))
                .andExpect(jsonPath("$.riepilogo.anzianita.anni").value(2))
                .andExpect(jsonPath("$.riepilogo.anzianita.dal").value("2023-01"))
                .andExpect(jsonPath("$.riepilogo.scatti.fonteAttuali").value("CEDOLINI"))
                .andExpect(jsonPath("$.riepilogo.scatti.attualiStimati").value(false))
                .andExpect(jsonPath("$.riepilogo.scatti.prossimoScatto").value("2026-02-01"))
                .andExpect(jsonPath("$.riepilogo.retribuzione.superminimo").value(300.12))
                .andExpect(jsonPath("$.riepilogo.retribuzione.contingenza").value(nullValue()))
                .andExpect(jsonPath("$.riepilogo.retribuzione.cedolinoRiferimentoId").value(12))
                .andExpect(jsonPath("$.riepilogo.buoniPasto").value(true))
                .andExpect(jsonPath("$.riepilogo.ratei.cedolinoRiferimentoId").value(12))
                .andExpect(jsonPath("$.riepilogo.ratei.saldi[0].saldo").value(12.5))
                .andExpect(jsonPath("$.riepilogo.ratei.saldi[0].valoreStimato").value(1129.81))
                .andExpect(jsonPath("$.riepilogo.ratei.saldi[0].nota").value("Paga giornaliera stampata"))
                .andExpect(jsonPath("$.riepilogo.tfr.totaleAccumulato").value(4500.0))
                .andExpect(jsonPath("$.riepilogo.tfr.aliquotaStimata").value(0.23))
                .andExpect(jsonPath("$.riepilogo.tfr.quotaMensileStimata").value(false))
                .andExpect(jsonPath("$.riepilogo.tfr.liquidato").value(false))
                .andExpect(jsonPath("$.riepilogo.tfr.importoLiquidato").value(nullValue()))
                .andExpect(jsonPath("$.riepilogo.tfr.nettoLiquidato").value(nullValue()))
                .andExpect(jsonPath("$.riepilogo.tfr.riferimento").value("2025-02"))
                .andExpect(jsonPath("$.riepilogo.liquidazione.dataRiferimento").value("2025-02-28"))
                .andExpect(jsonPath("$.riepilogo.liquidazione.voci[*].tipo")
                        .value(contains("TFR", "FERIE", "RATEI_QUATTORDICESIMA")))
                .andExpect(jsonPath("$.riepilogo.liquidazione.voci[1].quantita").value(12.5))
                .andExpect(jsonPath("$.riepilogo.liquidazione.voci[1].unita").value("GIORNI"))
                .andExpect(jsonPath("$.riepilogo.liquidazione.voci[2].lordo").value(nullValue()))
                .andExpect(jsonPath("$.riepilogo.liquidazione.totaleLordo").value(5629.81))
                .andExpect(jsonPath("$.riepilogo.liquidazione.totaleContributi").value(103.83))
                .andExpect(jsonPath("$.riepilogo.liquidazione.totaleImposte").value(1270.97))
                .andExpect(jsonPath("$.riepilogo.liquidazione.totaleNetto").value(4255.01))
                .andExpect(jsonPath("$.riepilogo.liquidazione.completa").value(false))
                .andExpect(jsonPath("$.riepilogo.liquidazione.aliquotaContributi").value(0.0919))
                .andExpect(jsonPath("$.riepilogo.liquidazione.aliquotaContributiStampata").value(true))
                .andExpect(jsonPath("$.riepilogo.liquidazione.nota").value("Stima"));
    }

    @Test
    void liquidazionePerLUltimaAziendaCessataENonPerLePrecedenti() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoConcluso())));
        when(calcoli.riepilogo(RAPPORTO)).thenReturn(Optional.of(DatiDiProva.riepilogo(List.of())));
        var ultimo = new RapportoSintesi(RAPPORTO, new PersonaId(1), new AziendaId(3), "Alfa Software S.r.l.",
                "01234567897", "Alfa Software S.r.l.", DatiDiProva.periodoConcluso(), "COMMERCIO");
        var precedente = new RapportoSintesi(new RapportoLavoroId(5), new PersonaId(1), new AziendaId(2),
                "Beta S.p.A.", null, "Beta S.p.A.", DatiDiProva.periodoConcluso(), null);

        when(anagrafica.rapportiDiPersona(new PersonaId(1))).thenReturn(List.of(ultimo, precedente));
        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riepilogo.liquidazione.voci", hasSize(3)));

        when(anagrafica.rapportiDiPersona(new PersonaId(1))).thenReturn(List.of(precedente, ultimo));
        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riepilogo.liquidazione").value(nullValue()));
    }

    @Test
    void rateiDelRiepilogoSoloPerIlRapportoAttuale() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoConcluso())));
        when(calcoli.riepilogo(RAPPORTO)).thenReturn(Optional.of(DatiDiProva.riepilogo(List.of())));

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attuale").value(false))
                .andExpect(jsonPath("$.periodo.a").value("2024-06"))
                .andExpect(jsonPath("$.riepilogo.ratei.cedolinoRiferimentoId").value(nullValue()))
                .andExpect(jsonPath("$.riepilogo.ratei.saldi", empty()))
                .andExpect(jsonPath("$.riepilogo.tfr.quotaInAzienda").value(4500.0))
                // i residui stampati sui singoli cedolini restano visibili nella lista mensilita'
                .andExpect(jsonPath("$.mensilita[7].ratei", hasSize(1)));
    }

    @Test
    void senzaRiepilogoNeSegnalazioniIlDettaglioNonFallisce() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(archivio.sintesiDiRapporto(RAPPORTO)).thenReturn(List.of());
        when(segnalazioni.mensilitaMancanti(RAPPORTO)).thenReturn(List.of());

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riepilogo").value(nullValue()))
                .andExpect(jsonPath("$.segnalazioni", empty()))
                .andExpect(jsonPath("$.mensilita", empty()));
    }

    @Test
    void rapportoInesistente404() throws Exception {
        when(anagrafica.dettaglioRapporto(new RapportoLavoroId(99))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/rapporti/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Rapporto di lavoro inesistente (id 99)."));
    }

    @Test
    void aggiornaLeNote() throws Exception {
        when(gestioneRapporti.aggiornaNote(RAPPORTO, "Chiedere il CUD"))
                .thenReturn(DatiDiProva.rapporto("COMMERCIO", "Chiedere il CUD"));

        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Chiedere il CUD\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"note\":\"Chiedere il CUD\"}"));
    }

    @Test
    void noteVuoteCancellate() throws Exception {
        when(gestioneRapporti.aggiornaNote(RAPPORTO, null)).thenReturn(DatiDiProva.rapporto("COMMERCIO", null));

        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.APPLICATION_JSON).content("{\"note\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(nullValue()));
    }

    @Test
    void noteTroppoLungheOCorpoNonValido400() throws Exception {
        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Le note non possono superare 4000 caratteri"));
        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.APPLICATION_JSON).content("{note"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo della richiesta mancante o non valido (JSON atteso)."));
        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.TEXT_PLAIN).content("testo"))
                .andExpect(status().isUnsupportedMediaType());
        verify(gestioneRapporti, never()).aggiornaNote(any(), any());
    }

    @Test
    void noteDiUnRapportoInesistente404() throws Exception {
        when(gestioneRapporti.aggiornaNote(new RapportoLavoroId(99), "x"))
                .thenThrow(new RisorsaNonTrovataException("Rapporto di lavoro", 99L));

        mvc.perform(put("/api/v1/rapporti/99/note").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void impostaIlCcnlERestituisceIlDettaglioRicalcolato() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO)).thenReturn(Optional.of(
                DatiDiProva.dettaglio("METALMECCANICO_INDUSTRIA", DatiDiProva.periodoAttuale())));

        mvc.perform(put("/api/v1/rapporti/7/ccnl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ccnlCodice\":\" METALMECCANICO_INDUSTRIA \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ccnl.codice").value("METALMECCANICO_INDUSTRIA"))
                .andExpect(jsonPath("$.mensilita", hasSize(11)));
        verify(gestioneRapporti).impostaCcnl(RAPPORTO, "METALMECCANICO_INDUSTRIA");
    }

    @Test
    void ccnlSconosciutoOMancante400() throws Exception {
        when(catalogoCcnl.trova("INESISTENTE")).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/rapporti/7/ccnl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ccnlCodice\":\"INESISTENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("CCNL sconosciuto: scegli un codice dall'elenco dei CCNL."));
        mvc.perform(put("/api/v1/rapporti/7/ccnl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ccnlCodice\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Indica il codice del CCNL"));
        mvc.perform(put("/api/v1/rapporti/7/ccnl").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(gestioneRapporti, never()).impostaCcnl(any(), anyString());
    }

    @Test
    void ccnlDiUnRapportoInesistente404() throws Exception {
        when(gestioneRapporti.impostaCcnl(new RapportoLavoroId(99), "COMMERCIO"))
                .thenThrow(new RisorsaNonTrovataException("Rapporto di lavoro", 99L));

        mvc.perform(put("/api/v1/rapporti/99/ccnl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ccnlCodice\":\"COMMERCIO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conflitti409() throws Exception {
        when(gestioneRapporti.impostaCcnl(RAPPORTO, "COMMERCIO"))
                .thenThrow(new OperazioneNonConsentitaException("Rapporto in aggiornamento: riprova."));
        when(gestioneRapporti.aggiornaNote(RAPPORTO, "x"))
                .thenThrow(new PessimisticLockingFailureException("lock"));

        mvc.perform(put("/api/v1/rapporti/7/ccnl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ccnlCodice\":\"COMMERCIO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operazione non consentita"))
                .andExpect(jsonPath("$.detail").value("Rapporto in aggiornamento: riprova."));
        mvc.perform(put("/api/v1/rapporti/7/note").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://salary-manager.local/problemi/modifica-concorrente"));
    }

    @Test
    void personaDelDettaglioSenzaEtichettaInElencoUsaNomeECognome() throws Exception {
        when(anagrafica.dettaglioRapporto(RAPPORTO))
                .thenReturn(Optional.of(DatiDiProva.dettaglio("COMMERCIO", DatiDiProva.periodoAttuale())));
        when(anagrafica.elencoPersone()).thenReturn(List.of());

        mvc.perform(get("/api/v1/rapporti/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.id").value(1))
                .andExpect(jsonPath("$.persona.etichetta").value("Mario Rossi"));
    }
}
