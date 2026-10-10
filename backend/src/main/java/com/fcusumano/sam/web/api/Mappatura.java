package com.fcusumano.sam.web.api;

import static com.fcusumano.sam.web.api.Numeri.esatto;
import static com.fcusumano.sam.web.api.Numeri.euro;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.web.api.dto.AziendaSintesiDto;
import com.fcusumano.sam.web.api.dto.CaricamentoDto;
import com.fcusumano.sam.web.api.dto.CcnlDto;
import com.fcusumano.sam.web.api.dto.CedolinoDettaglioDto;
import com.fcusumano.sam.web.api.dto.CompetenzaDto;
import com.fcusumano.sam.web.api.dto.EvidenzaDto;
import com.fcusumano.sam.web.api.dto.LottoCaricamentoDto;
import com.fcusumano.sam.web.api.dto.PeriodoDto;
import com.fcusumano.sam.web.api.dto.PersonaSintesiDto;
import com.fcusumano.sam.web.api.dto.RiepilogoDto;
import com.fcusumano.sam.web.api.dto.SegnalazioneDto;
import com.fcusumano.sam.web.api.dto.VoceDto;
import java.util.List;
import java.util.UUID;

/**
 * Conversione delle API pubbliche dei moduli nei DTO REST. Nessuna logica di dominio: solo forma e arrotondamento
 * di presentazione (importi al centesimo, quantita' senza zeri superflui).
 */
final class Mappatura {

    private Mappatura() {
    }

    // ------------------------------------------------------------------------------------------ anagrafica e CCNL

    static PersonaSintesiDto persona(PersonaSintesi p) {
        return new PersonaSintesiDto(p.id().valore(), p.nome(), p.cognome(), p.codiceFiscale(), p.etichetta());
    }

    static AziendaSintesiDto azienda(Azienda a) {
        return new AziendaSintesiDto(a.id().valore(), a.ragioneSociale(), a.partitaIva(), a.codiceFiscale(),
                a.posizioneInps(), a.indirizzo());
    }

    static PeriodoDto periodo(PeriodoRapporto p) {
        return new PeriodoDto(p.da(), p.a(), p.fonteInizio(), p.fonteFine());
    }

    static CcnlDto ccnl(Ccnl c) {
        return new CcnlDto(c.codice(), c.descrizione(), c.mensilita(), c.periodicitaScattiMesi(), c.maxScatti(),
                c.meseQuattordicesima(), c.noteVerifica());
    }

    static CompetenzaDto competenza(Competenza c) {
        if (c == null) {
            return null;
        }
        return switch (c) {
            case Competenza.Mensilita m -> new CompetenzaDto(Titoli.chiave(m), m.tipo(), m.anno(), m.mese(), null);
            case Competenza.FineRapporto f ->
                    new CompetenzaDto(Titoli.chiave(f), f.tipo(), f.anno(), null, f.progressivo());
        };
    }

    // ------------------------------------------------------------------------------------------ segnalazioni

    static SegnalazioneDto segnalazione(Segnalazione s) {
        return new SegnalazioneDto(s.id().valore(), s.tipo(), s.gravita(), s.titolo(), s.descrizione(),
                id(s.cedolinoId()), competenza(s.competenza()),
                s.evidenze().stream().map(Mappatura::evidenza).toList());
    }

    static EvidenzaDto evidenza(Evidenza e) {
        return new EvidenzaDto(e.descrizione(), id(e.cedolinoId()), competenza(e.competenza()), e.valore());
    }

    // ------------------------------------------------------------------------------------------ riepilogo

    /**
     * Riepilogo per la UI. I residui di ferie/permessi sono esposti solo per il rapporto attuale: per gli altri la
     * lista dei saldi e' vuota (e il cedolino di riferimento null), come previsto dal contratto.
     */
    static RiepilogoDto riepilogo(RiepilogoRapporto r, boolean rapportoAttuale) {
        var a = r.anzianita();
        var s = r.scatti();
        var rt = r.retribuzione();
        var t = r.tfr();
        var ratei = rapportoAttuale
                ? new RiepilogoDto.RateiRiepilogo(id(r.ratei().riferimento()),
                        r.ratei().saldi().stream().map(Mappatura::saldo).toList())
                : new RiepilogoDto.RateiRiepilogo(null, List.of());
        return new RiepilogoDto(
                r.calcolatoIl(),
                new RiepilogoDto.Anzianita(a.dal(), a.al(), a.inCorso(), a.anni(), a.mesi(), a.fonteInizio(),
                        a.fonteFine()),
                new RiepilogoDto.Scatti(s.attuali(), s.massimo(), s.prossimoScatto(), s.fonteProssimo(),
                        s.fonteAttuali(), s.attualiStimati()),
                new RiepilogoDto.Retribuzione(euro(rt.ral()), euro(rt.lordoMensile()), rt.mensilita(),
                        euro(rt.minimo()), euro(rt.contingenza()), euro(rt.superminimo()), euro(rt.importoScatti()),
                        euro(rt.altriElementiFissi()), euro(rt.retribuzioneBase()), rt.stimata(), rt.nota(),
                        id(rt.riferimento())),
                r.buoniPasto(),
                ratei,
                new RiepilogoDto.TfrRiepilogo(euro(t.totaleAccumulato()), euro(t.quotaMensile()),
                        euro(t.quotaInAzienda()), euro(t.quotaFondi()), euro(t.quotaBusta()), euro(t.anticipi()),
                        euro(t.stimaNetto()), esatto(t.aliquotaStimata()), t.stimato(), t.riferimento(), t.nota(),
                        t.quotaMensileStimata(), t.liquidato(), euro(t.importoLiquidato()), euro(t.nettoLiquidato())));
    }

    private static RiepilogoDto.SaldoRateo saldo(RiepilogoRapporto.SaldoRateo s) {
        return new RiepilogoDto.SaldoRateo(s.tipo(), s.unita(), esatto(s.saldo()), euro(s.valoreStimato()), s.nota());
    }

    // ------------------------------------------------------------------------------------------ cedolino

    static CedolinoDettaglioDto cedolino(CedolinoDettaglio dettaglio) {
        var c = dettaglio.dati();
        var e = c.elementiRetributivi();
        var p = c.presenze();
        var bp = c.buoniPasto();
        var pr = c.progressivi();
        return new CedolinoDettaglioDto(
                c.id().valore(),
                c.rapportoId().valore(),
                Titoli.di(c.competenza()),
                competenza(c.competenza()),
                c.periodoRetribuzione(),
                c.meseVariabili(),
                c.dataElaborazione(),
                dettaglio.nomeFile(),
                dettaglio.acquisitoIl(),
                esatto(dettaglio.confidenza()),
                dettaglio.campiNonTrovati(),
                dettaglio.avvisiEstrazione(),
                new CedolinoDettaglioDto.Inquadramento(c.qualifica(), c.livello(), c.codiceDipendente(), c.ccnlTesto(),
                        c.dataAssunzione(), c.dataCessazione()),
                new CedolinoDettaglioDto.Totali(euro(c.lordo()), euro(c.totaleCompetenze()), euro(c.totaleTrattenute()),
                        euro(c.arrotondamento()), euro(c.netto())),
                new CedolinoDettaglioDto.ElementiRetributivi(euro(e.minimo()), euro(e.contingenza()),
                        euro(e.superminimo()), e.superminimoAssorbibile(), euro(e.importoScatti()),
                        euro(e.altriElementiFissi()), euro(e.retribuzioneBase()), euro(e.totale()),
                        esatto(e.pagaOraria()), esatto(e.pagaGiornaliera()), esatto(e.divisoreOrario()),
                        esatto(e.divisoreGiornaliero())),
                new CedolinoDettaglioDto.Presenze(esatto(p.giorniLavorati()), esatto(p.oreLavorate()),
                        esatto(p.giorniRetribuiti()), voci(c, CategoriaVoce.PRESENZA)),
                voci(c, CategoriaVoce.COMPETENZA),
                voci(c, CategoriaVoce.TRATTENUTA_PREVIDENZIALE),
                voci(c, CategoriaVoce.TRATTENUTA_FISCALE),
                voci(c, CategoriaVoce.ALTRA_TRATTENUTA),
                voci(c, CategoriaVoce.NON_RETRIBUTIVA),
                voci(c, CategoriaVoce.INFORMATIVA),
                tfr(c.tfr()),
                c.ratei().stream().map(Mappatura::rateo).toList(),
                new CedolinoDettaglioDto.Scatti(c.numeroScatti(), c.prossimoScatto()),
                new CedolinoDettaglioDto.BuoniPasto(bp.presenti(), esatto(bp.numero()), euro(bp.valoreUnitario()),
                        euro(bp.importo())),
                new CedolinoDettaglioDto.Progressivi(euro(pr.imponibilePrevidenziale()), euro(pr.imponibileIrpef()),
                        euro(pr.irpefTrattenuta())),
                c.contieneTredicesima(),
                c.contieneQuattordicesima());
    }

    private static List<VoceDto> voci(CedolinoNormalizzato cedolino, CategoriaVoce categoria) {
        return cedolino.vociDi(categoria).stream().map(Mappatura::voce).toList();
    }

    static VoceDto voce(Voce v) {
        return new VoceDto(v.codice(), v.descrizione(), esatto(v.quantita()), v.unita(), esatto(v.base()),
                euro(v.importoCompetenza()), euro(v.importoTrattenuta()));
    }

    private static CedolinoDettaglioDto.Tfr tfr(DatiTfr t) {
        if (t == null) {
            return null;
        }
        return new CedolinoDettaglioDto.Tfr(euro(t.fondo31DicembreAnnoPrecedente()), euro(t.rivalutazione()),
                euro(t.impostaSostitutiva()), euro(t.quotaAnno()), euro(t.quotaMese()), euro(t.quotaFondi()),
                euro(t.quotaBusta()), euro(t.anticipi()), euro(t.retribuzioneUtile()), euro(t.liquidatoLordo()),
                euro(t.ritenuta()));
    }

    private static CedolinoDettaglioDto.Rateo rateo(Rateo r) {
        return new CedolinoDettaglioDto.Rateo(r.tipo(), r.unita(), esatto(r.residuoAnnoPrecedente()),
                esatto(r.maturato()), esatto(r.goduto()), esatto(r.saldo()));
    }

    // ------------------------------------------------------------------------------------------ caricamenti

    /** Stato di un file caricato, con persona e rapporto del cedolino acquisito quando noti. */
    static CaricamentoDto caricamento(Caricamento c) {
        return new CaricamentoDto(c.id().valore(), c.lotto(), c.nomeFileOriginale(), c.dimensione(), c.stato(),
                c.messaggio(), id(c.cedolinoId()), id(c.cedolinoEsistenteId()),
                c.personaId() == null ? null : c.personaId().valore(),
                c.rapportoId() == null ? null : c.rapportoId().valore(), c.creatoIl(), c.aggiornatoIl());
    }

    /** Lotto: completato quando ogni caricamento e' in uno stato terminale. */
    static LottoCaricamentoDto lotto(UUID lotto, List<Caricamento> caricamenti) {
        var completato = caricamenti.stream().allMatch(c -> c.stato().terminale());
        return new LottoCaricamentoDto(lotto, completato, caricamenti.stream().map(Mappatura::caricamento).toList());
    }

    // ------------------------------------------------------------------------------------------ supporto

    static Long id(CedolinoId id) {
        return id == null ? null : id.valore();
    }
}
