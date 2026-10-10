package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.shared.Competenza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Costruttore di cedolini normalizzati per i test delle regole (dati inventati). L'id del cedolino deriva dalla
 * competenza, quindi e' stabile e univoco nel rapporto.
 */
final class CedolinoDiProva {

    static final RapportoLavoroId RAPPORTO = new RapportoLavoroId(1);
    static final PersonaId PERSONA = new PersonaId(1);
    static final AziendaId AZIENDA = new AziendaId(1);

    private final Competenza competenza;
    private YearMonth periodo;
    private String livello = "5";
    private String qualifica = "Impiegato";
    private String ccnlTesto;
    private LocalDate assunzione;
    private LocalDate cessazione;
    private Integer numeroScatti;
    private BigDecimal importoScatti;
    private BigDecimal minimo;
    private BigDecimal retribuzioneBase;
    private LocalDate prossimoScatto;
    private boolean tredicesima;
    private boolean quattordicesima;
    private DatiTfr tfr;
    private ProgressiviAnnui progressivi;
    private final List<Voce> voci = new ArrayList<>();

    private CedolinoDiProva(Competenza competenza, YearMonth periodo) {
        this.competenza = competenza;
        this.periodo = periodo;
    }

    static CedolinoDiProva ordinario(int anno, int mese) {
        return new CedolinoDiProva(new Competenza.Mensilita(anno, mese), YearMonth.of(anno, mese));
    }

    static CedolinoDiProva tredicesima(int anno) {
        return new CedolinoDiProva(Competenza.Mensilita.tredicesima(anno), YearMonth.of(anno, 12)).conTredicesima();
    }

    static CedolinoDiProva quattordicesima(int anno, int meseErogazione) {
        return new CedolinoDiProva(Competenza.Mensilita.quattordicesima(anno), YearMonth.of(anno, meseErogazione))
                .conQuattordicesima();
    }

    static CedolinoDiProva fineRapporto(int progressivo, int anno, int mese) {
        var periodo = YearMonth.of(anno, mese);
        return new CedolinoDiProva(new Competenza.FineRapporto(progressivo, periodo), periodo);
    }

    /** Mensilita' ordinarie consecutive da {@code da} ad {@code a} compresi. */
    static List<CedolinoNormalizzato> ordinari(YearMonth da, YearMonth a) {
        var risultato = new ArrayList<CedolinoNormalizzato>();
        for (var mese = da; !mese.isAfter(a); mese = mese.plusMonths(1)) {
            risultato.add(ordinario(mese.getYear(), mese.getMonthValue()).build());
        }
        return risultato;
    }

    static BigDecimal euro(String importo) {
        return importo == null ? null : new BigDecimal(importo);
    }

    CedolinoDiProva periodo(YearMonth periodo) {
        this.periodo = periodo;
        return this;
    }

    CedolinoDiProva livello(String livello) {
        this.livello = livello;
        return this;
    }

    CedolinoDiProva qualifica(String qualifica) {
        this.qualifica = qualifica;
        return this;
    }

    CedolinoDiProva ccnl(String testo) {
        this.ccnlTesto = testo;
        return this;
    }

    CedolinoDiProva assunzione(LocalDate data) {
        this.assunzione = data;
        return this;
    }

    CedolinoDiProva cessazione(LocalDate data) {
        this.cessazione = data;
        return this;
    }

    CedolinoDiProva scatti(Integer numero, String importo) {
        this.numeroScatti = numero;
        this.importoScatti = euro(importo);
        return this;
    }

    CedolinoDiProva minimo(String importo) {
        this.minimo = euro(importo);
        return this;
    }

    CedolinoDiProva retribuzioneBase(String importo) {
        this.retribuzioneBase = euro(importo);
        return this;
    }

    CedolinoDiProva prossimoScatto(LocalDate data) {
        this.prossimoScatto = data;
        return this;
    }

    CedolinoDiProva conTredicesima() {
        this.tredicesima = true;
        return this;
    }

    CedolinoDiProva conQuattordicesima() {
        this.quattordicesima = true;
        return this;
    }

    /** Dati TFR principali (gli altri campi null). */
    CedolinoDiProva tfr(String fondo, String rivalutazione, String imposta, String quotaAnno) {
        this.tfr = new DatiTfr(euro(fondo), euro(rivalutazione), euro(imposta), euro(quotaAnno), null, null, null, null,
                null, null, null);
        return this;
    }

    CedolinoDiProva tfr(DatiTfr tfr) {
        this.tfr = tfr;
        return this;
    }

    CedolinoDiProva progressivi(String imponibilePrevidenziale, String imponibileIrpef, String irpef) {
        this.progressivi = new ProgressiviAnnui(euro(imponibilePrevidenziale), euro(imponibileIrpef), euro(irpef));
        return this;
    }

    CedolinoDiProva voce(String descrizione, CategoriaVoce categoria, String importo) {
        voci.add(new Voce(null, descrizione, categoria, null, null, null,
                categoria == CategoriaVoce.COMPETENZA ? euro(importo) : null,
                categoria == CategoriaVoce.COMPETENZA ? null : euro(importo)));
        return this;
    }

    CedolinoNormalizzato build() {
        var elementi = new ElementiRetributivi(minimo, null, null, null, importoScatti, null, retribuzioneBase, null,
                null, null, null, null);
        return new CedolinoNormalizzato(new CedolinoId(competenza.chiaveOrdinamento()), RAPPORTO, PERSONA, AZIENDA,
                competenza, periodo, null, null, assunzione, cessazione, qualifica, livello, null, ccnlTesto, null, null,
                null, null, null, elementi, voci, List.of(), tfr, null, numeroScatti, prossimoScatto, null,
                tredicesima, quattordicesima, progressivi);
    }
}
