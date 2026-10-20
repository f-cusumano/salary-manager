package com.fcusumano.sam.web.api;

import static com.fcusumano.sam.web.api.Numeri.esatto;
import static com.fcusumano.sam.web.api.Numeri.euro;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaSelezionabile;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.web.api.dto.AttribuzioneCaricamentoDto;
import com.fcusumano.sam.web.api.dto.AziendaSelezionabileDto;
import com.fcusumano.sam.web.api.dto.AziendaSintesiDto;
import com.fcusumano.sam.web.api.dto.CaricamentoDto;
import com.fcusumano.sam.web.api.dto.CaricamentoInErroreDto;
import com.fcusumano.sam.web.api.dto.CcnlDto;
import com.fcusumano.sam.web.api.dto.CedolinoDettaglioDto;
import com.fcusumano.sam.web.api.dto.CedolinoManualeDto;
import com.fcusumano.sam.web.api.dto.CompetenzaDto;
import com.fcusumano.sam.web.api.dto.EvidenzaDto;
import com.fcusumano.sam.web.api.dto.LottoCaricamentoDto;
import com.fcusumano.sam.web.api.dto.PeriodoDto;
import com.fcusumano.sam.web.api.dto.PersonaSintesiDto;
import com.fcusumano.sam.web.api.dto.RiepilogoDto;
import com.fcusumano.sam.web.api.dto.SegnalazioneDto;
import com.fcusumano.sam.web.api.dto.VerificaCedolinoManualeDto;
import com.fcusumano.sam.web.api.dto.VoceDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Conversione delle API pubbliche dei moduli nei DTO REST. Nessuna logica di dominio: solo forma e arrotondamento
 * di presentazione (importi al centesimo, quantita' senza zeri superflui).
 */
final class Mappatura {

    private Mappatura() {
    }

    // ------------------------------------------------------------------------------------------ anagrafica e CCNL

    static PersonaSintesiDto persona(PersonaSintesi p) {
        return new PersonaSintesiDto(p.id().valore(), p.nome(), p.cognome(), p.codiceFiscale(), p.dataNascita(),
                p.etichetta());
    }

    static AziendaSelezionabileDto aziendaSelezionabile(AziendaSelezionabile s) {
        var a = s.azienda();
        return new AziendaSelezionabileDto(a.id().valore(), a.ragioneSociale(), a.partitaIva(), a.codiceFiscale(),
                a.posizioneInps(), a.indirizzo(), a.codiceAzienda(), s.etichetta());
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
     * lista dei saldi e' vuota (e il cedolino di riferimento null), come previsto dal contratto. La liquidazione stimata
     * e' esposta solo se richiesto (rapporto attuale o ultimo della persona) e se si applica.
     */
    static RiepilogoDto riepilogo(RiepilogoRapporto r, boolean rapportoAttuale, boolean conLiquidazione) {
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
                        t.quotaMensileStimata(), t.liquidato(), euro(t.importoLiquidato()), euro(t.nettoLiquidato())),
                conLiquidazione && r.liquidazione().presente() ? liquidazione(r.liquidazione()) : null);
    }

    private static RiepilogoDto.Liquidazione liquidazione(RiepilogoRapporto.Liquidazione l) {
        var voci = l.voci().stream()
                .map(v -> new RiepilogoDto.VoceLiquidazione(v.tipo(), esatto(v.quantita()), v.unita(), euro(v.lordo()),
                        euro(v.contributi()), euro(v.imposte()), euro(v.netto()), v.nota()))
                .toList();
        return new RiepilogoDto.Liquidazione(
                l.dataRiferimento(),
                voci,
                somma(voci, RiepilogoDto.VoceLiquidazione::lordo),
                somma(voci, RiepilogoDto.VoceLiquidazione::contributi),
                somma(voci, RiepilogoDto.VoceLiquidazione::imposte),
                somma(voci, RiepilogoDto.VoceLiquidazione::netto),
                voci.stream().allMatch(v -> v.netto() != null),
                esatto(l.aliquotaContributi()),
                l.aliquotaContributiStampata(),
                esatto(l.aliquotaIrpefMarginale()),
                esatto(l.aliquotaTfr()),
                l.nota());
    }

    private static BigDecimal somma(List<RiepilogoDto.VoceLiquidazione> voci,
                                    Function<RiepilogoDto.VoceLiquidazione, BigDecimal> campo) {
        return euro(voci.stream().map(campo).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
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
                dettaglio.inseritoAMano(),
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
                c.messaggio(), c.motivo(), c.pdfDisponibile(), id(c.cedolinoId()), id(c.cedolinoEsistenteId()),
                c.personaId() == null ? null : c.personaId().valore(),
                c.rapportoId() == null ? null : c.rapportoId().valore(), c.creatoIl(), c.aggiornatoIl());
    }

    /** Lotto: completato quando ogni caricamento e' in uno stato terminale. */
    static LottoCaricamentoDto lotto(UUID lotto, List<Caricamento> caricamenti) {
        var completato = caricamenti.stream().allMatch(c -> c.stato().terminale());
        return new LottoCaricamentoDto(lotto, completato, caricamenti.stream().map(Mappatura::caricamento).toList());
    }

    // ------------------------------------------------------------------------------------------ file in errore

    /**
     * File in errore con la mensilita' associata gia' composta (etichette di persona e azienda dall'anagrafica).
     *
     * @param attribuzione null se il file non e' associato
     */
    static CaricamentoInErroreDto caricamentoInErrore(CaricamentoInErrore c, AttribuzioneCaricamentoDto attribuzione) {
        return new CaricamentoInErroreDto(c.id().valore(), c.lotto(), c.nomeFileOriginale(), c.dimensione(),
                c.motivo(), c.messaggio(), c.pdfDisponibile(), c.inElaborazione(), attribuzione, c.creatoIl());
    }

    static VerificaCedolinoManualeDto verifica(VerificaCedolinoManuale v) {
        return new VerificaCedolinoManualeDto(v.esito(), v.messaggio(), competenza(v.competenza()),
                v.competenza() == null ? null : Titoli.di(v.competenza()), v.nuovaPersona(), v.nuovaAzienda(),
                v.nuovoRapporto(),
                v.rapportoId() == null ? null : v.rapportoId().valore(), id(v.cedolinoEsistenteId()), v.avvisi());
    }

    /**
     * Cedolino inserito a mano: solo forma, nessuna normalizzazione (la fa il modulo cedolini, che valida anche i
     * dati e gli id delle anagrafiche scelte, passati cosi' come sono). Un'area assente resta null, le liste assenti
     * diventano vuote.
     */
    static CedolinoManuale cedolinoManuale(CedolinoManualeDto d) {
        return new CedolinoManuale(
                d.personaId(),
                d.aziendaId(),
                se(d.dipendente(), p -> new CedolinoManuale.Dipendente(p.codiceFiscale(), p.nome(), p.cognome(),
                        p.dataNascita(), p.codiceDipendente())),
                se(d.datoreLavoro(), a -> new CedolinoManuale.DatoreLavoro(a.codiceFiscale(), a.partitaIva(),
                        a.posizioneInps(), a.posizioneInail(), a.ragioneSociale(), a.indirizzo(), a.codiceAzienda())),
                se(d.datiContrattuali(), c -> new CedolinoManuale.DatiContrattuali(c.dataAssunzione(),
                        c.dataCessazione(), c.qualifica(), c.livello(), c.ccnl(), c.codiceCnel())),
                se(d.periodo(), p -> new CedolinoManuale.Periodo(p.anno(), p.mese(), p.tipo(), p.dataElaborazione(),
                        p.meseVariabili())),
                se(d.totali(), t -> new CedolinoManuale.Totali(t.lordo(), t.totaleCompetenze(), t.totaleTrattenute(),
                        t.arrotondamento(), t.netto())),
                se(d.retribuzione(), e -> new ElementiRetributivi(e.minimo(), e.contingenza(), e.superminimo(),
                        e.superminimoAssorbibile(), e.importoScatti(), e.altriElementiFissi(), e.retribuzioneBase(),
                        e.totale(), e.pagaOraria(), e.pagaGiornaliera(), e.divisoreOrario(), e.divisoreGiornaliero())),
                lista(d.voci(), v -> new Voce(v.codice(), v.descrizione(), v.categoria(), v.quantita(), v.unita(),
                        v.base(), v.importoCompetenza(), v.importoTrattenuta())),
                lista(d.ratei(), r -> new Rateo(r.tipo(), r.unita(), r.residuoAnnoPrecedente(), r.maturato(),
                        r.goduto(), r.saldo())),
                se(d.tfr(), t -> new DatiTfr(t.fondo31DicembreAnnoPrecedente(), t.rivalutazione(),
                        t.impostaSostitutiva(), t.quotaAnno(), t.quotaMese(), t.quotaFondi(), t.quotaBusta(),
                        t.anticipi(), t.retribuzioneUtile(), t.liquidatoLordo(), t.ritenuta())),
                se(d.presenze(), p -> new Presenze(p.giorniLavorati(), p.oreLavorate(), p.giorniRetribuiti())),
                se(d.scatti(), s -> new CedolinoManuale.Scatti(s.numeroScatti(), s.dataProssimoScatto())),
                se(d.buoniPasto(), b -> new BuoniPasto(b.presenti(), b.numero(), b.valoreUnitario(), b.importo())),
                se(d.progressivi(), p -> new ProgressiviAnnui(p.imponibilePrevidenziale(), p.imponibileIrpef(),
                        p.irpefTrattenuta())),
                d.contieneTredicesima(),
                d.contieneQuattordicesima());
    }

    /** Valore convertito, null se assente. */
    private static <T, R> R se(T valore, Function<T, R> conversione) {
        return valore == null ? null : conversione.apply(valore);
    }

    /**
     * Lista convertita, vuota se assente. Un elemento null resta null: la validazione del modulo cedolini lo segnala
     * sul suo percorso (es. {@code voci[2].descrizione}).
     */
    private static <T, R> List<R> lista(List<T> valori, Function<T, R> conversione) {
        if (valori == null) {
            return List.of();
        }
        return valori.stream().map(v -> se(v, conversione)).toList();
    }

    // ------------------------------------------------------------------------------------------ supporto

    static Long id(CedolinoId id) {
        return id == null ? null : id.valore();
    }
}
