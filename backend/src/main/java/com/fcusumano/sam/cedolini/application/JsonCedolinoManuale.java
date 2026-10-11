package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import java.math.BigDecimal;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cedolino inserito a mano come JSON dello schema di lettura ({@code prompts/cedolino-schema-v1.json}), lo stesso
 * prodotto dai lettori: passa poi da {@code InterpreteRispostaEstrazione} (mascheramento dei dati bancari, validazione,
 * normalizzazione, controlli) e resta come audit di quanto inserito. Convenzioni dello schema: stringa vuota per i
 * testi, gli importi e le date non indicati, 0 per anno e mese non indicati, importi in notazione decimale semplice,
 * date ISO. Un solo cedolino nel documento, confidenza piena (poi azzerata: nessuna lettura), nessun campo non
 * trovato e nessuna nota. Funzioni pure.
 */
final class JsonCedolinoManuale {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private JsonCedolinoManuale() {
    }

    /**
     * @param c cedolino validato e normalizzato ({@link ValidazioneCedolinoManuale}); i sotto-record null valgono come
     *          non stampati
     * @throws IllegalStateException se una voce non ha la categoria o un rateo non ha tipo e unita' (dati non validati)
     */
    static String scrivi(CedolinoManuale c) {
        return JSON.writeValueAsString(json(c));
    }

    static ObjectNode json(CedolinoManuale c) {
        var r = JSON.createObjectNode();
        r.put("numeroCedoliniNelDocumento", 1);

        var dipendente = c.dipendente() == null
                ? new CedolinoManuale.Dipendente(null, null, null, null, null) : c.dipendente();
        var d = r.putObject("dipendente");
        d.put("codiceFiscale", t(dipendente.codiceFiscale()));
        d.put("nome", t(dipendente.nome()));
        d.put("cognome", t(dipendente.cognome()));
        d.put("dataNascita", t(dipendente.dataNascita()));
        d.put("codiceDipendente", t(dipendente.codiceDipendente()));

        var datore = c.datoreLavoro() == null
                ? new CedolinoManuale.DatoreLavoro(null, null, null, null, null, null, null) : c.datoreLavoro();
        var a = r.putObject("datoreLavoro");
        a.put("codiceFiscale", t(datore.codiceFiscale()));
        a.put("partitaIva", t(datore.partitaIva()));
        a.put("posizioneInps", t(datore.posizioneInps()));
        a.put("posizioneInail", t(datore.posizioneInail()));
        a.put("ragioneSociale", t(datore.ragioneSociale()));
        a.put("indirizzo", t(datore.indirizzo()));
        a.put("codiceAzienda", t(datore.codiceAzienda()));

        var contratto = c.datiContrattuali() == null
                ? new CedolinoManuale.DatiContrattuali(null, null, null, null, null, null) : c.datiContrattuali();
        var k = r.putObject("datiContrattuali");
        k.put("dataAssunzione", t(contratto.dataAssunzione()));
        k.put("dataCessazione", t(contratto.dataCessazione()));
        k.put("qualifica", t(contratto.qualifica()));
        k.put("livello", t(contratto.livello()));
        k.put("ccnl", t(contratto.ccnl()));
        k.put("codiceCnel", t(contratto.codiceCnel()));

        var periodo = c.periodo() == null ? new CedolinoManuale.Periodo(null, null, null, null, null) : c.periodo();
        var p = r.putObject("periodo");
        p.put("anno", periodo.anno() == null ? 0 : periodo.anno());
        p.put("mese", periodo.mese() == null ? 0 : periodo.mese());
        p.put("tipo", periodo.tipo() == null ? TipoCedolinoEstratto.ORDINARIA.name()
                : TipoCedolinoEstratto.valueOf(periodo.tipo().name()).name());
        p.put("dataElaborazione", t(periodo.dataElaborazione()));
        p.put("meseVariabili", t(periodo.meseVariabili()));

        var totali = c.totali() == null ? new CedolinoManuale.Totali(null, null, null, null, null) : c.totali();
        var tot = r.putObject("totali");
        tot.put("lordo", t(totali.lordo()));
        tot.put("totaleCompetenze", t(totali.totaleCompetenze()));
        tot.put("totaleTrattenute", t(totali.totaleTrattenute()));
        tot.put("arrotondamento", t(totali.arrotondamento()));
        tot.put("netto", t(totali.netto()));

        var retribuzione = c.retribuzione() == null ? ElementiRetributivi.VUOTI : c.retribuzione();
        var re = r.putObject("retribuzione");
        re.put("minimo", t(retribuzione.minimo()));
        re.put("contingenza", t(retribuzione.contingenza()));
        re.put("superminimo", t(retribuzione.superminimo()));
        re.put("superminimoAssorbibile", retribuzione.superminimoAssorbibile() == null ? "NON_INDICATO"
                : retribuzione.superminimoAssorbibile() ? "SI" : "NO");
        re.put("importoScatti", t(retribuzione.importoScatti()));
        re.put("altriElementiFissi", t(retribuzione.altriElementiFissi()));
        re.put("retribuzioneBase", t(retribuzione.retribuzioneBase()));
        re.put("totale", t(retribuzione.totale()));
        re.put("pagaOraria", t(retribuzione.pagaOraria()));
        re.put("pagaGiornaliera", t(retribuzione.pagaGiornaliera()));
        re.put("divisoreOrario", t(retribuzione.divisoreOrario()));
        re.put("divisoreGiornaliero", t(retribuzione.divisoreGiornaliero()));

        var voci = r.putArray("voci");
        for (var v : c.voci()) {
            if (v == null || v.categoria() == null) {
                throw new IllegalStateException("Voce senza categoria: il cedolino va validato prima");
            }
            var n = voci.addObject();
            n.put("codice", t(v.codice()));
            n.put("descrizione", t(v.descrizione()));
            n.put("categoria", v.categoria().name());
            n.put("quantita", t(v.quantita()));
            n.put("unita", t(v.unita()));
            n.put("base", t(v.base()));
            n.put("importoCompetenza", t(v.importoCompetenza()));
            n.put("importoTrattenuta", t(v.importoTrattenuta()));
        }

        var ratei = r.putArray("ratei");
        for (var x : c.ratei()) {
            if (x == null || x.tipo() == null || x.unita() == null) {
                throw new IllegalStateException("Rateo senza tipo o unità: il cedolino va validato prima");
            }
            var n = ratei.addObject();
            n.put("tipo", x.tipo().name());
            n.put("unita", x.unita().name());
            n.put("residuoAnnoPrecedente", t(x.residuoAnnoPrecedente()));
            n.put("maturato", t(x.maturato()));
            n.put("goduto", t(x.goduto()));
            n.put("saldo", t(x.saldo()));
        }

        var tfr = c.tfr() == null
                ? new DatiTfr(null, null, null, null, null, null, null, null, null, null, null) : c.tfr();
        var f = r.putObject("tfr");
        f.put("fondo31DicembreAnnoPrecedente", t(tfr.fondo31DicembreAnnoPrecedente()));
        f.put("rivalutazione", t(tfr.rivalutazione()));
        f.put("impostaSostitutiva", t(tfr.impostaSostitutiva()));
        f.put("quotaAnno", t(tfr.quotaAnno()));
        f.put("quotaMese", t(tfr.quotaMese()));
        f.put("quotaFondi", t(tfr.quotaFondi()));
        f.put("quotaBusta", t(tfr.quotaBusta()));
        f.put("anticipi", t(tfr.anticipi()));
        f.put("retribuzioneUtile", t(tfr.retribuzioneUtile()));
        f.put("liquidatoLordo", t(tfr.liquidatoLordo()));
        f.put("ritenuta", t(tfr.ritenuta()));

        var presenze = c.presenze() == null ? Presenze.VUOTE : c.presenze();
        var pr = r.putObject("presenze");
        pr.put("giorniLavorati", t(presenze.giorniLavorati()));
        pr.put("oreLavorate", t(presenze.oreLavorate()));
        pr.put("giorniRetribuiti", t(presenze.giorniRetribuiti()));

        var scatti = c.scatti() == null ? new CedolinoManuale.Scatti(null, null) : c.scatti();
        var s = r.putObject("scatti");
        s.put("numeroScatti", t(scatti.numeroScatti()));
        s.put("dataProssimoScatto", t(scatti.dataProssimoScatto()));

        var buoniPasto = c.buoniPasto() == null ? BuoniPasto.ASSENTI : c.buoniPasto();
        var b = r.putObject("buoniPasto");
        b.put("presenti", buoniPasto.presenti());
        b.put("numero", t(buoniPasto.numero()));
        b.put("valoreUnitario", t(buoniPasto.valoreUnitario()));
        b.put("importo", t(buoniPasto.importo()));

        var progressivi = c.progressivi() == null ? ProgressiviAnnui.VUOTI : c.progressivi();
        var g = r.putObject("progressivi");
        g.put("imponibilePrevidenziale", t(progressivi.imponibilePrevidenziale()));
        g.put("imponibileIrpef", t(progressivi.imponibileIrpef()));
        g.put("irpefTrattenuta", t(progressivi.irpefTrattenuta()));

        r.put("contieneTredicesima", c.contieneTredicesima());
        r.put("contieneQuattordicesima", c.contieneQuattordicesima());
        var confidenza = r.putObject("confidenza");
        confidenza.put("complessiva", 1);
        confidenza.putArray("campi");
        r.putArray("campiNonTrovati");
        r.putArray("avvisi");
        return r;
    }

    /** Valore come stringa dello schema: vuota se assente, importi senza notazione esponenziale, date ISO. */
    private static String t(Object valore) {
        return switch (valore) {
            case null -> "";
            case BigDecimal importo -> importo.toPlainString();
            default -> valore.toString();
        };
    }
}
