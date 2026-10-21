package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dati di un cedolino archiviato come cedolino del modulo di inserimento a mano, per modificarli: il JSON
 * dell'estrazione archiviato (gia' senza dati bancari) e' interpretato di nuovo come alla lettura, cosi' i valori sono
 * quelli da cui e' stato prodotto il cedolino; posizione INAIL, CCNL e codice CNEL, che l'interpretazione non conserva
 * separati, si leggono dal JSON. Persona e azienda sono quelle del rapporto del cedolino, scelte dall'elenco. Le
 * voci senza descrizione o categoria e i ratei senza tipo o unita', non archiviati, sono omessi. Funzioni pure.
 */
final class DatiCedolinoArchiviato {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DatiCedolinoArchiviato() {
    }

    /**
     * @throws EstrazioneCedolinoException se il JSON archiviato non e' piu' interpretabile (schema cambiato)
     */
    static CedolinoManuale leggi(String json, String modello, RapportoLavoro rapporto,
                                 InterpreteRispostaEstrazione interprete) {
        var c = interprete.interpreta(json, modello).cedolino();
        var radice = JSON.readTree(json);
        var dipendente = c.dipendente();
        var datore = c.datoreLavoro();
        var contratto = c.datiContrattuali();
        var p = c.periodo();
        var t = c.totali();
        return new CedolinoManuale(
                rapporto.personaId().valore(),
                rapporto.aziendaId().valore(),
                dipendente == null ? null : new CedolinoManuale.Dipendente(dipendente.codiceFiscale(),
                        dipendente.nome(), dipendente.cognome(), data(dipendente.dataNascita()),
                        dipendente.codiceDipendente()),
                datore == null ? null : new CedolinoManuale.DatoreLavoro(datore.codiceFiscale(), datore.partitaIva(),
                        datore.posizioneInps(), testo(radice, "datoreLavoro", "posizioneInail"),
                        datore.ragioneSociale(), datore.indirizzo(), datore.codiceAzienda()),
                new CedolinoManuale.DatiContrattuali(
                        contratto == null ? null : data(contratto.dataAssunzione()),
                        contratto == null ? null : data(contratto.dataCessazione()),
                        contratto == null ? null : contratto.qualifica(),
                        contratto == null ? null : contratto.livello(),
                        testo(radice, "datiContrattuali", "ccnl"),
                        testo(radice, "datiContrattuali", "codiceCnel")),
                p == null ? null : new CedolinoManuale.Periodo(p.anno(), p.mese(), tipo(p.tipo()),
                        data(p.dataElaborazione()), meseVariabili(p)),
                t == null ? null : new CedolinoManuale.Totali(t.lordo(), t.totaleCompetenze(), t.totaleTrattenute(),
                        t.arrotondamento(), t.netto()),
                retribuzione(c.retribuzione()),
                voci(c.voci()),
                ratei(c.ratei()),
                tfr(c.tfr()),
                c.presenze() == null ? Presenze.VUOTE : new Presenze(c.presenze().giorniLavorati(),
                        c.presenze().oreLavorate(), c.presenze().giorniRetribuiti()),
                c.scatti() == null ? null : new CedolinoManuale.Scatti(c.scatti().numeroScatti(),
                        data(c.scatti().dataProssimoScatto())),
                buoniPasto(c.buoniPasto()),
                c.progressivi() == null ? ProgressiviAnnui.VUOTI : new ProgressiviAnnui(
                        c.progressivi().imponibilePrevidenziale(), c.progressivi().imponibileIrpef(),
                        c.progressivi().irpefTrattenuta()),
                Boolean.TRUE.equals(c.contieneTredicesima()),
                Boolean.TRUE.equals(c.contieneQuattordicesima()));
    }

    /** Il tipo AGGIUNTIVA non e' mai archiviato; senza tipo vale ORDINARIA, come all'acquisizione. */
    private static TipoMensilita tipo(CedolinoEstratto.TipoCedolinoEstratto tipo) {
        return tipo == null || tipo == CedolinoEstratto.TipoCedolinoEstratto.AGGIUNTIVA ? TipoMensilita.ORDINARIA
                : TipoMensilita.valueOf(tipo.name());
    }

    private static YearMonth meseVariabili(CedolinoEstratto.Periodo p) {
        return p.annoVariabili() != null && p.meseVariabili() != null && p.meseVariabili() >= 1
                && p.meseVariabili() <= 12 ? YearMonth.of(p.annoVariabili(), p.meseVariabili()) : null;
    }

    private static ElementiRetributivi retribuzione(CedolinoEstratto.Retribuzione r) {
        return r == null ? ElementiRetributivi.VUOTI : new ElementiRetributivi(r.minimo(), r.contingenza(),
                r.superminimo(), r.superminimoAssorbibile(), r.importoScatti(), r.altriElementiFissi(),
                r.retribuzioneBase(), r.totale(), r.pagaOraria(), r.pagaGiornaliera(), r.divisoreOrario(),
                r.divisoreGiornaliero());
    }

    private static List<Voce> voci(List<CedolinoEstratto.VoceEstratta> voci) {
        return voci == null ? List.of() : voci.stream()
                .filter(v -> v.descrizione() != null && v.categoria() != null)
                .map(v -> new Voce(v.codice(), v.descrizione(), v.categoria(), v.quantita(), v.unita(), v.base(),
                        v.importoCompetenza(), v.importoTrattenuta()))
                .toList();
    }

    private static List<Rateo> ratei(List<CedolinoEstratto.RateoEstratto> ratei) {
        return ratei == null ? List.of() : ratei.stream()
                .filter(r -> r.tipo() != null && r.unita() != null)
                .map(r -> new Rateo(r.tipo(), r.unita(), r.residuoAnnoPrecedente(), r.maturato(), r.goduto(),
                        r.saldo()))
                .toList();
    }

    /** Null se il cedolino non riporta dati TFR. */
    private static DatiTfr tfr(CedolinoEstratto.Tfr t) {
        if (t == null || Stream.of(t.fondo31DicembreAnnoPrecedente(), t.rivalutazione(), t.impostaSostitutiva(),
                t.quotaAnno(), t.quotaMese(), t.quotaFondi(), t.quotaBusta(), t.anticipi(), t.retribuzioneUtile(),
                t.liquidatoLordo(), t.ritenuta()).allMatch(Objects::isNull)) {
            return null;
        }
        return new DatiTfr(t.fondo31DicembreAnnoPrecedente(), t.rivalutazione(), t.impostaSostitutiva(), t.quotaAnno(),
                t.quotaMese(), t.quotaFondi(), t.quotaBusta(), t.anticipi(), t.retribuzioneUtile(),
                t.liquidatoLordo(), t.ritenuta());
    }

    private static BuoniPasto buoniPasto(CedolinoEstratto.BuoniPasto b) {
        return b == null ? BuoniPasto.ASSENTI
                : new BuoniPasto(Boolean.TRUE.equals(b.presenti()), b.numero(), b.valoreUnitario(), b.importo());
    }

    /** Testo del JSON (spazi esterni tolti), null se assente o vuoto. */
    private static String testo(JsonNode radice, String gruppo, String campo) {
        var nodo = radice.path(gruppo).path(campo);
        if (!nodo.isString()) {
            return null;
        }
        var valore = nodo.asString().strip();
        return valore.isEmpty() ? null : valore;
    }

    /** Data ISO dell'estrazione interpretata; null se assente o non valida. */
    private static LocalDate data(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
