package com.fcusumano.sam.cedolini.adapter.locale;

import static com.fcusumano.sam.cedolini.adapter.locale.PdfSintetico.COURIER;
import static com.fcusumano.sam.cedolini.adapter.locale.PdfSintetico.HELVETICA;

import java.util.List;

/**
 * Cedolini sintetici con i layout supportati dal lettore locale, riprodotti con le posizioni dei cedolini veri e
 * valori inventati e coerenti (somme, netto, TFR, ratei). Dipendente "Mario Rossi" (RSSMRA80A01H501U), aziende
 * "Alfa Software S.r.l." e "Beta Servizi S.r.l."; l'IBAN e' inventato e serve a verificare che i dati bancari non
 * vengano mai estratti.
 */
final class CedoliniSintetici {

    static final String CODICE_FISCALE = "RSSMRA80A01H501U";
    static final String IBAN = "IT60X0542811101000000123456";

    private CedoliniSintetici() {
    }

    // ------------------------------------------------------------------------------------------------ JOB Sistemi

    /** Voce di un cedolino JOB: importi come stampati, null se la colonna e' vuota. */
    record VoceJob(String codice, String descrizione, String unita, String quantita, String base, String trattenuta,
                   String competenza, boolean nelNetto) {
    }

    /** Dati variabili di un cedolino JOB. */
    record Job(String periodo, String cessazione, List<VoceJob> voci, String irpef, String totaleCompetenze,
               String totaleTrattenute, String arrotondamentoPrecedente, String arrotondamentoAttuale,
               String netto, String irpefTrattenutaAnno) {

        /** Aprile 2024: competenze 2.045,50, trattenute 511,14, netto 1.534,00. */
        static Job ordinaria() {
            return new Job("APRILE 2024", null, List.of(
                    new VoceJob("1", "Retribuzione ordinaria", "GIORNI", "26,000", "76,92308", null, "2.000,00", true),
                    new VoceJob("302", "Festivita' goduta", "GIORNI", "1,000", "76,92308", null, null, true),
                    new VoceJob("250", "Ferie godute", "ore", "8,000", null, null, null, true),
                    new VoceJob("702", "Rimborso spese km", null, null, null, null, "45,50", true),
                    new VoceJob("820", "Buoni pasto", null, "20,000", "8,00000", null, "160,00", false),
                    new VoceJob("910", "Quota sindacale", null, null, null, "15,00", null, true)),
                    "312,34", "2.045,50", "511,14", "0,41", "0,05", "1.534,00", null);
        }

        /** Tredicesima 2024 (intestazione senza mese): competenze 2.000,00, trattenute 603,80, netto 1.396,40. */
        static Job tredicesima() {
            return new Job("13^ MENSILITA' 2024", null, List.of(
                    new VoceJob("40", "Tredicesima mensilita'", null, null, null, null, "2.000,00", true)),
                    "420,00", "2.000,00", "603,80", "0,00", "0,20", "1.396,40", "6.120,00");
        }

        /** Aprile 2024 con fine rapporto il 31/03/2024: competenze 1.513,08, trattenute 439,05, netto 1.075,00. */
        static Job fineRapporto() {
            return new Job("APRILE 2024", "31/03/2024", List.of(
                    new VoceJob("260", "Ferie non godute", "GIORNI", "13,170", "76,92308", null, "1.013,08", true),
                    new VoceJob("45", "Rateo tredicesima", null, null, null, null, "500,00", true)),
                    "300,00", "1.513,08", "439,05", "0,00", "0,97", "1.075,00", null);
        }

        Job senzaNetto() {
            return new Job(periodo, cessazione, voci, irpef, totaleCompetenze, totaleTrattenute,
                    arrotondamentoPrecedente, arrotondamentoAttuale, null, irpefTrattenutaAnno);
        }
    }

    /** Cedolino JOB Sistemi ("Paghe Web"): etichette in Helvetica 5, valori in Helvetica 7. */
    static byte[] jobSistemi(Job j) {
        var pdf = new PdfSintetico().pagina();
        var e = new Scrittura(pdf, HELVETICA, 5);
        var v = new Scrittura(pdf, HELVETICA, 7);

        // azienda e periodo
        e.t(223.1f, 39.6f, "POS. INPS").t(283.2f, 39.6f, "MESE DI RETRIBUZIONE");
        v.t(205.4f, 41.6f, "1234");
        v.t(392.8f, 49.2f, j.periodo());
        v.t(223.1f, 50.5f, "1234567890");
        v.t(30.6f, 51.9f, "ALFA SOFTWARE S.R.L.");
        e.t(223.1f, 60.3f, "POS. INAIL");
        v.t(30.6f, 61.5f, "VIA DELLE ROSE, 10");
        v.t(30.6f, 71.3f, "00100 ROMA (RM)").t(224.5f, 71.3f, "12345678/90");
        e.t(30.6f, 80.5f, "C.F.:");
        v.t(49.7f, 81.6f, "01234567897");
        e.t(30.6f, 91.5f, "P.IVA:");
        v.t(49.7f, 92.8f, "01234567897").t(487.3f, 92.8f, "05/05/2024");
        e.t(448.4f, 93.7f, "Stampato il");

        // dipendente e contratto
        e.t(30.6f, 101.6f, "COD.DIP.");
        e.t(66.1f, 103.1f, "COGNOME E NOME").t(261.4f, 103.1f, "CODICE FISCALE").t(348.7f, 103.1f, "NATO A");
        v.t(38.9f, 113.2f, "1234").t(66.1f, 113.2f, "ROSSI MARIO").t(261.4f, 113.2f, CODICE_FISCALE)
                .t(347.4f, 113.2f, "ROMA (RM)").t(505.8f, 113.2f, "IL").t(517.6f, 113.2f, "01/01/1980");
        e.t(261.4f, 123.7f, "DESCRIZIONE QUALIFICA").t(385.6f, 123.7f, "CONTRATTO DI LAVORO")
                .t(456f, 123.7f, "(CNEL H011)");
        e.t(30.6f, 125.2f, "INDIRIZZO").t(520.8f, 125.2f, "N°");
        v.t(66.1f, 126.3f, "VIA DEI GIGLI, 5");
        v.t(261.4f, 135.6f, "IMPIEGATO").t(385.6f, 135.6f, "COMMERCIO TERZIARIO").t(543.6f, 135.6f, "1234");
        e.t(30.6f, 145.8f, "ASSUNZIONE").t(75.6f, 145.8f, "ANZ. CONV.").t(217.7f, 145.8f, "FINE RAPPORTO")
                .t(271.0f, 145.8f, "CENTRO DI COSTO").t(407.4f, 145.8f, "SEDE DI LAVORO");
        v.t(38.0f, 157.4f, "01/09/2015");
        if (j.cessazione() != null) {
            v.t(220.0f, 157.4f, j.cessazione());
        }
        e.t(30.6f, 167.9f, "MODALITA' DI PAGAMENTO").t(165.7f, 167.9f, "RIFERIMENTI BANCARI")
                .t(329.6f, 167.9f, "SCATTI ANZIANITA'").t(464.8f, 167.9f, "LIVELLO").t(511.2f, 167.9f, "% PART TIME");
        e.t(370.6f, 174.7f, "DATA");
        v.t(30.6f, 179.4f, "BONIFICO").t(165.7f, 179.4f, "IT60 X054 2811 1010 0000 0123 456")
                .t(329.6f, 179.4f, "N.").t(340.0f, 179.4f, "2").t(409.4f, 179.4f, "01/10/2025").t(479.9f, 179.4f, "4");
        e.t(370.6f, 181.6f, "PROSSIMO");

        // ratei ed elementi della retribuzione
        e.t(184.9f, 187.2f, "GODUTI");
        e.t(286.0f, 189.8f, "MINIMO").t(341.9f, 189.8f, "SUPERMINIMO");
        e.t(244.6f, 191.3f, "RESIDUI");
        e.t(46.7f, 194.6f, "RATEI").t(83.4f, 194.6f, "RESIDUI A.P.").t(125.5f, 194.6f, "MATURATI");
        e.t(172.7f, 197.4f, "A.P.").t(210.5f, 197.4f, "A.C.").t(246.8f, 197.4f, "TOTALI");
        v.t(302.4f, 200.4f, "1.800,00000").t(366.2f, 200.4f, "200,00000");
        rateoJob(e, v, 209.1f, "FERIE", "(GIORNI)", "12,00", "2,17", "1,00", null, "13,17");
        rateoJob(e, v, 228.5f, "PERMESSI (R.O.L.)", "(ORE)", "40,00", "6,00", "5,00", "3,00", "38,00");
        rateoJob(e, v, 247.8f, "EX FESTIVITA'", "(ORE)", "8,00", "2,67", null, null, "10,67");
        e.t(286.0f, 265.8f, "RETRIBUZIONE ORARIA").t(378.7f, 265.8f, "RETRIBUZIONE GIORNALIERA")
                .t(473.0f, 265.8f, "RETRIBUZIONE MENSILE");
        v.r(373.4f, 276.0f, "11,56069").r(463.5f, 276.0f, "76,92308").r(560.5f, 276.0f, "2.000,00");

        // voci
        e.t(279.1f, 284.5f, "Unita'").t(295.7f, 284.5f, "di").t(524.9f, 284.5f, "C").t(537.1f, 284.5f, "I")
                .t(548.2f, 284.5f, "T").t(556.3f, 284.5f, "N");
        e.t(31.9f, 287.3f, "VOCE").t(53.8f, 287.3f, "DESCRIZIONE").t(321.5f, 287.3f, "Quantita'")
                .t(373.3f, 287.3f, "Base").t(418.3f, 287.3f, "TRATTENUTE").t(471.6f, 287.3f, "COMPETENZE");
        e.t(280.4f, 290.0f, "misura");
        float y = 300.8f;
        for (var voce : j.voci()) {
            v.r(45.6f, y, voce.codice()).t(51.1f, y, voce.descrizione());
            if (voce.unita() != null) {
                v.t(275.0f, y, voce.unita());
            }
            v.r(352.4f, y, voce.quantita()).r(405.7f, y, voce.base()).r(461.6f, y, voce.trattenuta())
                    .r(517.6f, y, voce.competenza());
            v.t(526.2f, y, "*").t(535.8f, y, "*");
            if (voce.nelNetto()) {
                v.t(556.3f, y, "*");
            }
            y += 11.05f;
        }
        v.t(51.1f, 400.1f, "Gli elementi variabili della retribuzione sono");
        v.t(51.1f, 411.1f, "relativi al calendario presenze/assenze sono di 3/2024");

        // contributi
        for (float x0 : new float[] {31.9f, 298.2f}) {
            float dx = x0 - 31.9f;
            e.t(x0, 592.5f, "DESCRIZIONE CONTRIBUTO").t(107.0f + dx, 592.5f, "IMPONIBILE")
                    .t(150.7f + dx, 592.5f, "% C/DIP").t(175.3f + dx, 592.5f, "C/DIPENDENTE")
                    .t(217.7f + dx, 592.5f, "C/DITTA:");
        }
        var imponibile = j.totaleCompetenze().equals("2.045,50") ? "2.000,00" : j.totaleCompetenze();
        var inps = j.totaleCompetenze().equals("2.045,50") ? "183,80"
                : j.totaleCompetenze().equals("2.000,00") ? "183,80" : "139,05";
        v.t(30.6f, 603.7f, "INPS").r(146.4f, 603.7f, imponibile).r(172.7f, 603.7f, "9,190").r(213.2f, 603.7f, inps);
        v.t(30.6f, 613.1f, "FONDO SANITARIO").r(292.4f, 613.1f, "10,00");

        // presenze e imponibili contributivi
        e.t(31.3f, 640.8f, "Q").t(38.8f, 640.8f, "SETT. RETR.").t(73.0f, 640.8f, "GG. RETR.")
                .t(105.7f, 640.8f, "GG. LAV.").t(135.7f, 640.8f, "ORE LAV.").t(175.3f, 640.8f, "IMPON.CONTRIBUTIVO ANNO")
                .t(260.0f, 640.8f, "CONTRIBUTI ANNO").t(341.9f, 640.8f, "IMPON.CONTRIBUTIVO MESE")
                .t(421.1f, 640.8f, "IMPON.CONTRIB. ARROT. MESE").t(503.0f, 640.8f, "TOTALE CONTRIBUTI");
        v.t(55.8f, 652.0f, "4").t(83.9f, 652.0f, "26").t(113.5f, 652.0f, "21").t(138.5f, 652.0f, "168,00")
                .t(214.6f, 652.0f, "8.000,00").t(310.8f, 652.0f, "735,20").t(390.0f, 652.0f, imponibile)
                .t(473.3f, 652.0f, imponibile).t(535.8f, 652.0f, inps);

        // fisco
        e.t(40.1f, 661.4f, "IMPONIBILE FISCALE").t(101.6f, 661.4f, "IRPEF LORDA");
        v.t(70.4f, 672.6f, "1.816,20").t(137.0f, 672.6f, "450,00");
        e.t(40.1f, 683.5f, "IRPEF NETTA").t(503.0f, 683.5f, "IRPEF + IMP. SOST.");
        v.t(75.6f, 694.7f, j.irpef()).t(537.1f, 694.7f, j.irpef());
        e.t(40.1f, 705.6f, "IMPONIBILE FISCALE").t(101.6f, 705.6f, "IRPEF LORDA");
        v.t(67.1f, 718.2f, "7.264,80");
        e.t(40.1f, 727.7f, "IRPEF NETTA").t(101.6f, 727.7f, "IRPEF TRATTENUTA").t(164.4f, 727.7f, "IRPEF CONGUAGLIO")
                .t(503.0f, 727.7f, "CONG.IRPEF+IMP.SOST.");
        if (j.irpefTrattenutaAnno() != null) {
            v.r(146.0f, 738.0f, j.irpefTrattenutaAnno());
        }

        // TFR
        e.t(40.1f, 749.7f, "RETRIBUZIONE UTILE TFR").t(108.4f, 749.7f, "CONTR. AGG. TFR").t(157.6f, 749.7f, "TFR MESE")
                .t(202.7f, 749.7f, "TFR ANNUO PROGR.").t(257.3f, 749.7f, "F.DO TFR 31/12 AP")
                .t(317.3f, 749.7f, "ANTICIPAZIONI ANNO").t(376.1f, 749.7f, "TFR SPETTANTE AZIENDA")
                .t(445.7f, 749.7f, "TFR A F.DO PENSIONE");
        v.t(77.3f, 760.9f, "2.000,00").t(135.0f, 760.9f, "10,00").t(179.4f, 760.9f, "138,15")
                .t(226.1f, 760.9f, "552,60").t(286.2f, 760.9f, "5.000,00").t(415.9f, 760.9f, "5.552,60");

        // totali
        e.t(244.9f, 791.0f, "TOTALE COMPETENZE").t(309.1f, 791.0f, "TOTALE TRATTENUTE").t(374.6f, 791.0f, "ARR. PRECED.")
                .t(422.5f, 791.0f, "ARR. ATTUALE").t(471.6f, 791.0f, "NETTO IN BUSTA");
        v.r(293.9f, 802.3f, j.totaleCompetenze()).r(358.1f, 802.3f, j.totaleTrattenute())
                .r(414.0f, 802.3f, j.arrotondamentoPrecedente()).r(464.5f, 802.3f, j.arrotondamentoAttuale())
                .r(558.5f, 802.3f, j.netto());
        return pdf.pdf();
    }

    private static void rateoJob(Scrittura e, Scrittura v, float y, String etichetta, String unita, String residuo,
                                 String maturato, String godutoAp, String godutoAc, String saldo) {
        e.t(30.6f, y, etichetta);
        v.r(113.6f, y + 2.1f, residuo).r(151.8f, y + 2.1f, maturato).r(190.1f, y + 2.1f, godutoAp)
                .r(228.4f, y + 2.1f, godutoAc).r(270.6f, y + 2.1f, saldo);
        e.t(30.6f, y + 7.4f, unita);
    }

    // -------------------------------------------------------------------------------------------------- Zucchetti

    /**
     * Cedolino Zucchetti di marzo 2020 (assunzione il 16/03/2020, variabili di febbraio): etichette in Helvetica 6,
     * valori in Courier 10 con i campi adiacenti attaccati. Competenze 953,08, trattenute 114,53 (con un importo
     * negativo), arrotondamento 0,45, netto 839,00.
     *
     * @param recente grafica recente: etichette con una "s" al posto degli spazi e date separate con il trattino
     */
    static byte[] zucchetti(boolean recente) {
        var pdf = new PdfSintetico().pagina();
        var e = new Scrittura(pdf, HELVETICA, 6).recente(recente);
        var v = new Scrittura(pdf, COURIER, 10);

        e.t(21.6f, 27.8f, "CODICE AZIENDA").t(81.6f, 27.8f, "RAGIONE SOCIALE");
        v.t(18.0f, 46.0f, "0000000123ALFA SOFTWARE S.R.L.");
        e.t(21.6f, 51.8f, "INDIRIZZO:");
        v.t(54.0f, 58.0f, "VIA DELLE ROSE, 10");
        v.t(54.0f, 70.0f, "00100 ROMA (RM)");
        e.t(21.6f, 76.1f, "CODICE FISCALE").t(117.6f, 76.1f, "POSIZIONE INPS").t(194.9f, 76.1f, "P.A.T. INAIL")
                .t(266.9f, 76.1f, "UNITA' LOCALE");
        v.t(18.0f, 94.0f, "01234567897").t(114.0f, 94.0f, "1234567890/0012345678/90 Sede di Roma");
        v.t(420.0f, 106.0f, recente ? "05-04-2020" : "05/04/2020");
        e.t(423.6f, 110.2f, "PERIODO DI RETRIBUZIONE");
        v.t(432.0f, 118.0f, "Marzo 2020");
        v.t(432.0f, 130.0f, "Variabili di").t(510.0f, 130.0f, "02/2020");

        e.t(21.6f, 135.8f, "Codice dipendente").t(87.6f, 135.8f, "Cognome e nome").t(441.6f, 135.8f, "Codice fiscale")
                .t(537.6f, 135.8f, "Matricola");
        v.t(18.0f, 154.0f, "00000001234ROSSI MARIO").t(438.0f, 154.0f, CODICE_FISCALE);
        e.t(24.7f, 159.8f, "Data di nascita").t(71.0f, 159.8f, "Data assunzione").t(119.3f, 159.8f, "Data cessazione");
        v.t(162.0f, 166.0f, "Impiegato 4' Livello");
        if (recente) {
            v.t(22.0f, 178.0f, "01-01-1980").t(88.0f, 178.0f, "16-03-2020");
        } else {
            v.t(18.0f, 178.0f, "0101198016032020");
        }
        var p = new Scrittura(pdf, HELVETICA, 6);
        p.t(21.6f, 190.1f, "Sett.").t(44.5f, 190.1f, "Giorni").t(81.3f, 190.1f, "Ore").t(99.6f, 190.1f, "GG")
                .t(134.6f, 190.1f, "Giorni").t(169.7f, 190.1f, "Ore ordinarie").t(214.4f, 190.1f, "Ore straordinarie")
                .t(262.0f, 190.1f, "Detrazioni");
        v.t(24.0f, 202.0f, "2").t(48.0f, 202.0f, "12").t(138.0f, 202.0f, "11").t(180.0f, 202.0f, "88,00");
        v.t(66.0f, 214.0f, "MINIMO").t(150.0f, 214.0f, "SUP.ASS.");
        v.t(78.0f, 226.0f, "1.800,00000").t(174.0f, 226.0f, "200,00000");
        p.t(37.7f, 255.9f, "Prossimo");
        e.t(518.4f, 258.0f, "TOTALE");
        p.t(40.8f, 262.1f, "scatto");
        v.t(30.0f, 274.0f, "092025").t(498.0f, 274.0f, "2.000,00000");

        e.t(100.1f, 279.8f, "Voci variabili del mese").t(251.8f, 279.8f, "Importo base")
                .t(349.7f, 279.8f, "Riferimento").t(440.9f, 279.8f, "Trattenute").t(516.5f, 279.8f, "Competenze");
        v.t(18.0f, 298.0f, "**1001").t(60.0f, 298.0f, "Retribuzione ordinaria").t(264.0f, 298.0f, "76,92308")
                .t(342.0f, 298.0f, "12,00000").t(402.0f, 298.0f, "GG").r(564.0f, 298.0f, "923,08");
        v.t(30.0f, 310.0f, "2050").t(60.0f, 310.0f, "Rimborso chilometrico").t(270.0f, 310.0f, "0,30000")
                .t(342.0f, 310.0f, "100,00000").t(402.0f, 310.0f, "KM").r(564.0f, 310.0f, "30,00");
        v.t(18.0f, 322.0f, "*").t(30.0f, 322.0f, "9001").t(60.0f, 322.0f, "Contributo IVS").t(258.0f, 322.0f, "923,08")
                .t(348.0f, 322.0f, "9,19000%").r(486.0f, 322.0f, "84,83");
        v.t(30.0f, 334.0f, "9101").t(60.0f, 334.0f, "Imponibile IRPEF").r(306.0f, 334.0f, "868,25");
        v.t(30.0f, 346.0f, "9102").t(60.0f, 346.0f, "IRPEF lorda").r(306.0f, 346.0f, "199,70");
        v.t(30.0f, 358.0f, "9110").t(60.0f, 358.0f, "Ritenute IRPEF").r(486.0f, 358.0f, "79,70");
        v.t(30.0f, 370.0f, "9500").t(60.0f, 370.0f, "Trattamento integrativo").r(492.0f, 370.0f, "50,00-");
        v.t(60.0f, 382.0f, "Retribuzione utile T.F.R.").r(306.0f, 382.0f, "953,08");
        v.t(60.0f, 394.0f, "Quota T.F.R.").r(306.0f, 394.0f, "70,60");

        e.t(21.6f, 616.8f, "CONGUAGLIO");
        e.t(21.6f, 652.6f, "PROGRESSIVI");
        v.t(60.0f, 658.0f, "Imp. INPS").t(144.0f, 658.0f, "Imp. INAIL").t(234.0f, 658.0f, "Imp. IRPEF")
                .t(300.0f, 658.0f, "IRPEF pagata");
        v.r(120.0f, 670.0f, "923,08").r(204.0f, 670.0f, "923,08").r(288.0f, 670.0f, "838,25").r(372.0f, 670.0f, "79,70");
        v.t(66.0f, 682.0f, "F.do 31/12").t(138.0f, 682.0f, "Rivalutaz.").t(204.0f, 682.0f, "Imp.rival.")
                .t(276.0f, 682.0f, "Quota anno").t(342.0f, 682.0f, "TFR a fondi").t(426.0f, 682.0f, "TFR a QuIR")
                .t(510.0f, 682.0f, "Anticipi");
        v.r(126.0f, 694.0f, "0,00").r(336.0f, 694.0f, "70,60");

        e.t(23.3f, 700.6f, "RATEI");
        e.t(426.5f, 703.4f, "TOTALE COMPETENZE");
        v.t(96.0f, 706.0f, "Residuo AP").t(174.0f, 706.0f, "Maturato").t(252.0f, 706.0f, "Goduto")
                .t(330.0f, 706.0f, "Saldo").r(564.0f, 706.0f, "953,08");
        e.t(426.5f, 715.4f, "TOTALE TRATTENUTE");
        v.t(30.0f, 718.0f, "Ferie").r(162.0f, 718.0f, "0,00000").r(240.0f, 718.0f, "1,08333")
                .r(312.0f, 718.0f, "0,00000").r(384.0f, 718.0f, "1,08333").t(390.0f, 718.0f, "GG.")
                .r(564.0f, 718.0f, "114,53");
        e.t(428.2f, 727.2f, "ARROTONDAMENTO");
        v.t(30.0f, 730.0f, "Permessi").r(162.0f, 730.0f, "0,00000").r(240.0f, 730.0f, "4,33333")
                .r(312.0f, 730.0f, "0,00000").r(384.0f, 730.0f, "4,33333").t(390.0f, 730.0f, "Ore")
                .r(564.0f, 730.0f, "0,45");
        if (recente) {
            e.t(457.9f, 737.8f, "NETTO DEL MESE");
        } else {
            p.t(457.9f, 737.8f, "N E T T O   D E L   M E S E");
        }
        v.t(480.0f, 754.0f, "839,00");
        p.t(448.1f, 813.4f, "Modello per procedure ZUCCHETTI");
        return pdf.pdf();
    }

    // ------------------------------------------------------------------------------------------- modulo M39320

    /**
     * Cedolino su modulo M39320 (Courier 7,2, campi per posizione) di "Beta Servizi S.r.l.".
     *
     * @param fineRapporto false: marzo 2020 con cessazione il 15/03/2020 (competenze 1.028,92, trattenute 222,48,
     *                     netto 807,00); true: cedolino di aprile 2020 dopo la cessazione con TFR liquidato
     *                     (competenze 4.748,83, trattenute 1.092,23, netto 3.657,00)
     */
    static byte[] moduloM39320(boolean fineRapporto) {
        var pdf = new PdfSintetico().pagina();
        var v = new Scrittura(pdf, COURIER, 7.2f);
        v.t(31.8f, 20.0f, "Elaborato da : STUDIO GAMMA PAGHE");
        v.t(31.8f, 44.0f, "BT0042").t(67.8f, 44.0f, "-").t(132.6f, 44.0f, "BETA SERVIZI S.R.L.")
                .t(436.4f, 44.0f, "9876543210");
        v.t(116.8f, 68.2f, "57").t(132.6f, 68.2f, "ROSSI").t(334.2f, 68.2f, "MARIO");
        v.t(39.0f, 92.2f, "VIA DEI GIGLI, 5 - 00100 - ROMA (RM)");
        v.t(39.0f, 116.3f, "IMPIEGATO -").t(168.6f, 116.3f, "PROGRAMMATORE - 4").t(396.1f, 116.3f, "1 - 87654321/12");
        v.t(46.2f, 140.3f, "01/01/1980").t(111.0f, 140.3f, "01/02/2016").t(172.0f, 140.3f, "15/03/2020")
                .t(262.2f, 140.3f, CODICE_FISCALE);
        if (fineRapporto) {
            v.t(39.0f, 164.3f, "30/04/2020").t(103.8f, 164.3f, "04/2020 - Aprile");
        } else {
            v.t(39.0f, 164.3f, "31/03/2020").t(103.8f, 164.3f, "03/2020 - Marzo").t(247.8f, 164.3f, "84,00")
                    .t(302.5f, 164.3f, "11").t(345.7f, 164.3f, "15");
        }
        v.t(39.0f, 188.3f, "RETRIBUZIONE BASE :").t(157.1f, 188.3f, "1.800,00");
        v.t(39.0f, 248.5f, "Paga oraria").t(125.4f, 248.5f, ":").t(161.4f, 248.5f, "10,40462")
                .t(219.0f, 248.5f, "Paga giorn.").t(305.4f, 248.5f, ":").t(337.1f, 248.5f, "69,23077")
                .t(384.6f, 248.5f, "Totale").t(435.2f, 248.5f, ":").t(513.0f, 248.5f, "1.800,00");
        String totaleTrattenute;
        String totaleCompetenze;
        String netto;
        String arrotondamentoAttuale;
        if (fineRapporto) {
            voceM(v, 272.5f, "410", "T.F.R. 31/12 A.P.").t(341.4f, 272.5f, "(").r(403.3f, 272.5f, "4.500,00)");
            voceM(v, 284.5f, "411", "RIV. TFR").t(341.4f, 284.5f, "(").r(403.3f, 284.5f, "45,00)");
            voceM(v, 296.6f, "412", "IMP. SOST. TFR").t(341.4f, 296.6f, "(").r(403.3f, 296.6f, "7,65)");
            voceM(v, 308.6f, "413", "T.F.R. ANNO").t(341.4f, 308.6f, "(").r(403.3f, 308.6f, "211,48)");
            voceM(v, 332.6f, "420", "IND. ANZ. LIQUIDATA").r(551.6f, 332.6f, "4.748,83");
            voceM(v, 344.6f, "425", "TFR IMPON. SEPARATA").r(479.6f, 344.6f, "1.092,23");
            totaleTrattenute = "1.092,23";
            totaleCompetenze = "4.748,83";
            netto = "3.657,00";
            arrotondamentoAttuale = "0,40";
        } else {
            voceM(v, 272.5f, "1", "RETRIBUZIONE ORDINARIA").r(275.2f, 272.5f, "15,00").r(406.2f, 272.5f, "60,00000")
                    .r(551.6f, 272.5f, "900,00");
            voceM(v, 284.5f, "2", "ORE STRAORDINARIE").r(275.2f, 284.5f, "4,00").r(406.2f, 284.5f, "12,98077")
                    .r(551.6f, 284.5f, "51,92");
            voceM(v, 296.6f, "120", "FERIE GODUTE").r(275.2f, 296.6f, "2,00");
            v.t(31.8f, 320.6f, "_".repeat(120));
            voceM(v, 332.6f, "500", "IMP.PREV. MESE").r(400.4f, 332.6f, "951,92");
            voceM(v, 344.6f, "7", "CONTR.IVS").r(329.9f, 344.6f, "9,1900").r(400.4f, 344.6f, "951,92")
                    .r(479.6f, 344.6f, "87,48");
            voceM(v, 356.8f, "650", "BUONI PASTO").r(275.2f, 356.8f, "11,00").r(406.2f, 356.8f, "7,00000")
                    .r(551.6f, 356.8f, "77,00");
            v.t(67.8f, 368.8f, "INAIL Pos: 87654321/12").t(341.4f, 368.8f, "(").r(403.3f, 368.8f, "951,92)");
            v.t(67.8f, 404.9f, "IMPONIBILE FISCALE").r(400.4f, 404.9f, "864,44");
            v.t(67.8f, 428.9f, "IRPEF LORDA").r(400.4f, 428.9f, "198,82");
            voceM(v, 476.9f, "740", "RITENUTE IRPEF").r(479.6f, 476.9f, "120,00");
            voceM(v, 488.9f, "755", "ADD.REG. ACCONTO").r(479.6f, 488.9f, "15,00");
            totaleTrattenute = "222,48";
            totaleCompetenze = "1.028,92";
            netto = "807,00";
            arrotondamentoAttuale = "0,56";
        }
        v.r(478.2f, 561.1f, totaleTrattenute).r(550.2f, 561.1f, totaleCompetenze);
        v.t(103.8f, 585.1f, "Prossimo scatto").t(183.0f, 585.1f, "Scatto maturato").r(546.6f, 585.1f, netto);
        v.t(115.3f, 597.2f, "01/02/2022").r(322.7f, 597.2f, "0,00").r(401.9f, 597.2f, arrotondamentoAttuale);
        v.t(82.2f, 609.2f, "Gg.").t(262.2f, 609.2f, "Ore").t(442.2f, 609.2f, "Ore");
        if (fineRapporto) {
            rateiM(v, 621.2f, "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00");
            rateiM(v, 633.2f, "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00");
        } else {
            rateiM(v, 621.2f, "5,00", "3,00", "2,00", "8,00", "8,00", "0,00", "20,00", "12,00", "8,00");
            rateiM(v, 633.2f, "6,50", "4,00", "2,50", "8,00", "0,00", "8,00", "18,00", "6,00", "12,00");
        }
        v.t(341.4f, 645.2f, "TFR Al 31/12/19");
        v.r(106.7f, 657.4f, "951,92").r(178.7f, 657.4f, "2.598,32").r(257.9f, 657.4f, "360,00")
                .r(329.9f, 657.4f, "0,00").r(409.1f, 657.4f, "4.500,00");
        v.t(67.8f, 680.0f, "Il calendario delle presenze e' relativo al mese precedente.");
        v.t(528.6f, 825.6f, "(M39320)");
        return pdf.pdf();
    }

    private static Scrittura voceM(Scrittura v, float y, String codice, String descrizione) {
        return v.r(52.0f, y, codice).t(67.8f, y, descrizione);
    }

    private static void rateiM(Scrittura v, float y, String... valori) {
        float[] destre = {83.6f, 134.0f, 184.4f, 256.4f, 306.8f, 357.2f, 443.6f, 494.0f, 544.4f};
        for (int i = 0; i < destre.length; i++) {
            v.r(destre[i], y, valori[i]);
        }
    }

    // --------------------------------------------------------------------------------------------------- altri

    /** Scansione: una pagina con la sola immagine, nessun testo. */
    static byte[] scansione() {
        return new PdfSintetico().pagina().immagine(20, 20, 555, 800).pdf();
    }

    /** Documento testuale che non e' un cedolino noto. */
    static byte[] layoutSconosciuto() {
        var pdf = new PdfSintetico().pagina();
        var v = new Scrittura(pdf, HELVETICA, 10);
        v.t(40, 60, "Gamma Consulting S.r.l. - Prospetto paga mensile");
        v.t(40, 90, "Dipendente: Mario Rossi   Codice fiscale: " + CODICE_FISCALE);
        v.t(40, 110, "Mese: Aprile 2024   Qualifica: Impiegato   Livello: 4");
        v.t(40, 140, "Stipendio base 2.000,00   Contributi 183,80   Imposte 312,34");
        v.t(40, 160, "Importo netto da corrispondere 1.504,00");
        return pdf.pdf();
    }

    /** Scrive testi con un carattere fisso; i valori null vengono saltati. */
    private static final class Scrittura {

        private final PdfSintetico pdf;
        private final org.apache.pdfbox.pdmodel.font.PDFont font;
        private final float dimensione;
        private boolean recente;

        Scrittura(PdfSintetico pdf, org.apache.pdfbox.pdmodel.font.PDFont font, float dimensione) {
            this.pdf = pdf;
            this.font = font;
            this.dimensione = dimensione;
        }

        Scrittura recente(boolean recente) {
            this.recente = recente;
            return this;
        }

        Scrittura t(float x, float y, String testo) {
            if (testo != null) {
                pdf.carattere(font, dimensione).testo(x, y, recente ? testo.replace(" ", "s") : testo);
            }
            return this;
        }

        Scrittura r(float x1, float y, String testo) {
            if (testo != null) {
                pdf.carattere(font, dimensione).aDestra(x1, y, testo);
            }
            return this;
        }
    }
}
