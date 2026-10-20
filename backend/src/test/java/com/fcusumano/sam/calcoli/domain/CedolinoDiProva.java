package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Costruttore di cedolini normalizzati per i test del dominio (dati inventati). L'id di default deriva dalla chiave
 * cronologica della competenza, quindi e' univoco per competenza.
 */
final class CedolinoDiProva {

    static final RapportoLavoroId RAPPORTO = new RapportoLavoroId(7);
    static final Ccnl METALMECCANICO = new Ccnl("METALMECCANICO_INDUSTRIA", "Metalmeccanico industria", 13, 24, 5,
            null, null);
    static final Ccnl COMMERCIO = new Ccnl("COMMERCIO", "Terziario, distribuzione e servizi", 14, 36, 10, 7, null);
    static final Ccnl TELECOMUNICAZIONI = new Ccnl("TELECOMUNICAZIONI", "Telecomunicazioni", 14, null, null, 7,
            "Verificare scatti");
    static final ParametriCalcolo PARAMETRI = new ParametriCalcolo(
            new BigDecimal("26"), new BigDecimal("173"), new BigDecimal("8"), new BigDecimal("13.5"),
            new BigDecimal("0.23"), new BigDecimal("0.17"), new BigDecimal("0.01"), 13, 3, new BigDecimal("0.03"), 12);
    /** Scaglioni IRPEF 2024-2025 e dal 2026, contributi INPS di default 9,19%. */
    static final ParametriLiquidazione PARAMETRI_LIQUIDAZIONE = new ParametriLiquidazione(new BigDecimal("0.0919"),
            List.of(
                    new ParametriLiquidazione.ScaglioniIrpef(2024, List.of(
                            new ParametriLiquidazione.Scaglione(new BigDecimal("28000"), new BigDecimal("0.23")),
                            new ParametriLiquidazione.Scaglione(new BigDecimal("50000"), new BigDecimal("0.35")),
                            new ParametriLiquidazione.Scaglione(null, new BigDecimal("0.43")))),
                    new ParametriLiquidazione.ScaglioniIrpef(2026, List.of(
                            new ParametriLiquidazione.Scaglione(new BigDecimal("28000"), new BigDecimal("0.23")),
                            new ParametriLiquidazione.Scaglione(new BigDecimal("50000"), new BigDecimal("0.33")),
                            new ParametriLiquidazione.Scaglione(null, new BigDecimal("0.43"))))));

    private Long id;
    private final Competenza competenza;
    private YearMonth periodo;
    private LocalDate assunzione;
    private LocalDate cessazione;
    private ElementiRetributivi elementi = ElementiRetributivi.VUOTI;
    private final List<Voce> voci = new ArrayList<>();
    private final List<Rateo> ratei = new ArrayList<>();
    private DatiTfr tfr;
    private Integer numeroScatti;
    private LocalDate prossimoScatto;
    private boolean contieneTredicesima;
    private boolean contieneQuattordicesima;
    private BuoniPasto buoniPasto = BuoniPasto.ASSENTI;

    private CedolinoDiProva(Competenza competenza, YearMonth periodo) {
        this.competenza = competenza;
        this.periodo = periodo;
    }

    static CedolinoDiProva ordinario(int anno, int mese) {
        return new CedolinoDiProva(new Competenza.Mensilita(anno, mese), YearMonth.of(anno, mese));
    }

    static CedolinoDiProva tredicesima(int anno) {
        return new CedolinoDiProva(Competenza.Mensilita.tredicesima(anno), YearMonth.of(anno, 12));
    }

    static CedolinoDiProva quattordicesima(int anno) {
        return new CedolinoDiProva(Competenza.Mensilita.quattordicesima(anno), YearMonth.of(anno, 7));
    }

    static CedolinoDiProva fineRapporto(int progressivo, int anno, int mese) {
        var periodo = YearMonth.of(anno, mese);
        return new CedolinoDiProva(new Competenza.FineRapporto(progressivo, periodo), periodo);
    }

    static ContestoRapporto contesto(LocalDate assunzione, LocalDate cessazione, Ccnl ccnl) {
        return new ContestoRapporto(RAPPORTO, assunzione, cessazione, ccnl);
    }

    static BigDecimal euro(String valore) {
        return new BigDecimal(valore);
    }

    CedolinoDiProva id(long valore) {
        this.id = valore;
        return this;
    }

    CedolinoDiProva date(LocalDate assunzione, LocalDate cessazione) {
        this.assunzione = assunzione;
        this.cessazione = cessazione;
        return this;
    }

    /** Elementi scomposti: minimo + superminimo + scatti, con totale stampato. */
    CedolinoDiProva scomposti(String minimo, String superminimo, String scatti) {
        var m = euro(minimo);
        var s = superminimo == null ? null : euro(superminimo);
        var sc = scatti == null ? null : euro(scatti);
        var totale = m.add(s == null ? BigDecimal.ZERO : s).add(sc == null ? BigDecimal.ZERO : sc);
        this.elementi = new ElementiRetributivi(m, null, s, Boolean.TRUE, sc, null, null, totale,
                null, null, null, null);
        return this;
    }

    CedolinoDiProva elementi(ElementiRetributivi elementi) {
        this.elementi = elementi;
        return this;
    }

    /** Sola retribuzione base, stampata anche come totale. */
    CedolinoDiProva base(String importo) {
        this.elementi = new ElementiRetributivi(null, null, null, null, null, null, euro(importo), euro(importo),
                null, null, null, null);
        return this;
    }

    CedolinoDiProva voce(String descrizione, CategoriaVoce categoria, String importo) {
        voci.add(new Voce(null, descrizione, categoria, null, null, null, euro(importo), null));
        return this;
    }

    CedolinoDiProva rateo(TipoRateo tipo, UnitaMisura unita, String saldo) {
        ratei.add(new Rateo(tipo, unita, null, null, null, saldo == null ? null : euro(saldo)));
        return this;
    }

    CedolinoDiProva tfr(DatiTfr dati) {
        this.tfr = dati;
        return this;
    }

    CedolinoDiProva scatti(Integer numero, LocalDate prossimo) {
        this.numeroScatti = numero;
        this.prossimoScatto = prossimo;
        return this;
    }

    /** Il cedolino paga la tredicesima (o suoi ratei) insieme alla mensilita'. */
    CedolinoDiProva conTredicesima() {
        this.contieneTredicesima = true;
        return this;
    }

    /** Voce con quantita' (es. aliquota percentuale di un contributo). */
    CedolinoDiProva voce(String descrizione, CategoriaVoce categoria, String quantita, String importo) {
        voci.add(new Voce(null, descrizione, categoria, euro(quantita), "%", null, null, euro(importo)));
        return this;
    }

    /** Il cedolino paga la quattordicesima (o suoi ratei) insieme alla mensilita'. */
    CedolinoDiProva conQuattordicesima() {
        this.contieneQuattordicesima = true;
        return this;
    }

    CedolinoDiProva buoniPasto() {
        this.buoniPasto = new BuoniPasto(true, euro("20"), euro("8"), euro("160"));
        return this;
    }

    CedolinoNormalizzato build() {
        var identificativo = id != null ? id : competenza.chiaveOrdinamento();
        return new CedolinoNormalizzato(
                new CedolinoId(identificativo), RAPPORTO, new PersonaId(1), new AziendaId(1),
                competenza, periodo, null, null, assunzione, cessazione,
                "Impiegato", "5", null, null,
                null, null, null, null, null,
                elementi, voci, ratei, tfr, null, numeroScatti, prossimoScatto, buoniPasto,
                contieneTredicesima, contieneQuattordicesima, null);
    }

    /** Dati TFR con i soli campi indicati (gli altri null). */
    static DatiTfr datiTfr(String fondo, String rivalutazione, String imposta, String quotaAnno, String quotaMese,
                           String fondi, String busta, String anticipi, String retribuzioneUtile, String liquidato,
                           String ritenuta) {
        return new DatiTfr(n(fondo), n(rivalutazione), n(imposta), n(quotaAnno), n(quotaMese), n(fondi), n(busta),
                n(anticipi), n(retribuzioneUtile), n(liquidato), n(ritenuta));
    }

    private static BigDecimal n(String valore) {
        return valore == null ? null : new BigDecimal(valore);
    }
}
