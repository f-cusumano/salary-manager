package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.domain.TipoDichiarato;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Conversione del cedolino estratto (gia' validato e normalizzato dall'interprete) nei tipi del modulo.
 * Funzioni pure.
 */
final class ConversioneCedolinoEstratto {

    private ConversioneCedolinoEstratto() {
    }

    /** Periodo di retribuzione stampato, se anno e mese sono validi. */
    static Optional<YearMonth> periodo(CedolinoEstratto c) {
        var p = c.periodo();
        if (p == null || p.anno() == null || p.mese() == null || p.mese() < 1 || p.mese() > 12) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(p.anno(), p.mese()));
    }

    /** Tipo dichiarato; ORDINARIA se il modello non lo indica. */
    static TipoDichiarato tipo(CedolinoEstratto c) {
        if (c.periodo() == null || c.periodo().tipo() == null) {
            return TipoDichiarato.ORDINARIA;
        }
        return TipoDichiarato.valueOf(c.periodo().tipo().name());
    }

    static LocalDate dataAssunzione(CedolinoEstratto c) {
        return c.datiContrattuali() == null ? null : data(c.datiContrattuali().dataAssunzione());
    }

    static LocalDate dataCessazione(CedolinoEstratto c) {
        return c.datiContrattuali() == null ? null : data(c.datiContrattuali().dataCessazione());
    }

    static LocalDate dataNascita(CedolinoEstratto c) {
        return c.dipendente() == null ? null : data(c.dipendente().dataNascita());
    }

    /** Dati normalizzati da salvare (senza id). */
    static CedolinoNormalizzato normalizzato(CedolinoEstratto c, Competenza competenza, YearMonth periodo,
                                             RapportoLavoro rapporto) {
        var contratto = c.datiContrattuali();
        var p = c.periodo();
        var totali = c.totali();
        YearMonth meseVariabili = p.annoVariabili() != null && p.meseVariabili() != null
                && p.meseVariabili() >= 1 && p.meseVariabili() <= 12
                ? YearMonth.of(p.annoVariabili(), p.meseVariabili()) : null;
        var lordo = totali == null ? null : totali.lordo() != null ? totali.lordo() : totali.totaleCompetenze();
        return new CedolinoNormalizzato(
                null,
                rapporto.id(),
                rapporto.personaId(),
                rapporto.aziendaId(),
                competenza,
                periodo,
                meseVariabili,
                data(p.dataElaborazione()),
                dataAssunzione(c),
                dataCessazione(c),
                contratto == null ? null : contratto.qualifica(),
                contratto == null ? null : contratto.livello(),
                c.dipendente() == null ? null : c.dipendente().codiceDipendente(),
                contratto == null ? null : contratto.ccnl(),
                lordo,
                totali == null ? null : totali.totaleCompetenze(),
                totali == null ? null : totali.totaleTrattenute(),
                totali == null ? null : totali.arrotondamento(),
                totali == null ? null : totali.netto(),
                elementi(c.retribuzione()),
                voci(c.voci()),
                ratei(c.ratei()),
                tfr(c.tfr()),
                presenze(c.presenze()),
                c.scatti() == null ? null : c.scatti().numeroScatti(),
                c.scatti() == null ? null : data(c.scatti().dataProssimoScatto()),
                buoniPasto(c.buoniPasto()),
                Boolean.TRUE.equals(c.contieneTredicesima()),
                Boolean.TRUE.equals(c.contieneQuattordicesima()),
                progressivi(c.progressivi()));
    }

    private static ElementiRetributivi elementi(CedolinoEstratto.Retribuzione r) {
        if (r == null) {
            return ElementiRetributivi.VUOTI;
        }
        return new ElementiRetributivi(r.minimo(), r.contingenza(), r.superminimo(), r.superminimoAssorbibile(),
                r.importoScatti(), r.altriElementiFissi(), r.retribuzioneBase(), r.totale(), r.pagaOraria(),
                r.pagaGiornaliera(), r.divisoreOrario(), r.divisoreGiornaliero());
    }

    private static List<Voce> voci(List<CedolinoEstratto.VoceEstratta> voci) {
        if (voci == null) {
            return List.of();
        }
        return voci.stream()
                .filter(v -> v.descrizione() != null && v.categoria() != null)
                .map(v -> new Voce(v.codice(), v.descrizione(), v.categoria(), v.quantita(), v.unita(), v.base(),
                        v.importoCompetenza(), v.importoTrattenuta()))
                .toList();
    }

    private static List<Rateo> ratei(List<CedolinoEstratto.RateoEstratto> ratei) {
        if (ratei == null) {
            return List.of();
        }
        return ratei.stream()
                .filter(r -> r.tipo() != null && r.unita() != null)
                .map(r -> new Rateo(r.tipo(), r.unita(), r.residuoAnnoPrecedente(), r.maturato(), r.goduto(), r.saldo()))
                .toList();
    }

    private static DatiTfr tfr(CedolinoEstratto.Tfr t) {
        if (t == null) {
            return null;
        }
        return new DatiTfr(t.fondo31DicembreAnnoPrecedente(), t.rivalutazione(), t.impostaSostitutiva(), t.quotaAnno(),
                t.quotaMese(), t.quotaFondi(), t.quotaBusta(), t.anticipi(), t.retribuzioneUtile(), t.liquidatoLordo(),
                t.ritenuta());
    }

    private static Presenze presenze(CedolinoEstratto.Presenze p) {
        return p == null ? Presenze.VUOTE : new Presenze(p.giorniLavorati(), p.oreLavorate(), p.giorniRetribuiti());
    }

    private static BuoniPasto buoniPasto(CedolinoEstratto.BuoniPasto b) {
        if (b == null || !Boolean.TRUE.equals(b.presenti())) {
            return BuoniPasto.ASSENTI;
        }
        return new BuoniPasto(true, b.numero(), b.valoreUnitario(), b.importo());
    }

    private static ProgressiviAnnui progressivi(CedolinoEstratto.Progressivi p) {
        return p == null ? ProgressiviAnnui.VUOTI
                : new ProgressiviAnnui(p.imponibilePrevidenziale(), p.imponibileIrpef(), p.irpefTrattenuta());
    }

    private static LocalDate data(String iso) {
        return iso == null || iso.isBlank() ? null : LocalDate.parse(iso);
    }
}
