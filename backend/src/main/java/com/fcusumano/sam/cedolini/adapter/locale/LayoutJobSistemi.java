package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.adapter.locale.CedolinoLetto.Rateo;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Area;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Etichetta;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Pagina;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Parola;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cedolino JOB Sistemi (libro unico "Paghe Web"): un foglio A4 a riquadri con etichette in maiuscolo ("MESE DI
 * RETRIBUZIONE", "COD.DIP. COGNOME E NOME", "NETTO IN BUSTA"), voci con le colonne TRATTENUTE/COMPETENZE e i flag
 * C I T N (N = considerato nel netto), riquadri dei contributi, delle presenze (Q), del fisco e del TFR.
 * <p>
 * Le posizioni sono sempre relative alle etichette: lo stesso layout stampato su pagine di formato diverso (A4 o
 * Letter, con margini diversi) si legge allo stesso modo.
 */
final class LayoutJobSistemi implements LayoutCedolino {

    static final String ID = "job-sistemi";

    private static final Pattern MESE_RETRIBUZIONE = Zone.etichetta("MESE DI RETRIBUZIONE");
    private static final Pattern NETTO_IN_BUSTA = Zone.etichetta("NETTO IN BUSTA");
    private static final Pattern TFR_SPETTANTE = Zone.etichetta("TFR SPETTANTE AZIENDA");
    private static final Pattern POS_INPS = Zone.etichetta("POS. INPS");
    private static final Pattern POS_INAIL = Zone.etichetta("POS. INAIL");
    private static final Pattern CF_AZIENDA = Zone.etichetta("C.F.:");
    private static final Pattern PARTITA_IVA = Zone.etichetta("P.IVA:");
    private static final Pattern STAMPATO = Zone.etichetta("Stampato il");
    private static final Pattern COD_DIP = Zone.etichetta("COD.DIP.");
    private static final Pattern COGNOME_NOME = Zone.etichetta("COGNOME E NOME");
    private static final Pattern CODICE_FISCALE = Zone.etichetta("CODICE FISCALE");
    private static final Pattern NATO_A = Zone.etichetta("NATO A");
    private static final Pattern QUALIFICA = Zone.etichetta("DESCRIZIONE QUALIFICA");
    private static final Pattern CONTRATTO = Zone.etichetta("CONTRATTO DI LAVORO");
    private static final Pattern NUMERO = Zone.etichetta("N°");
    private static final Pattern CNEL = Pattern.compile("CNEL-?([A-Z]\\d{3})");
    private static final Pattern ASSUNZIONE = Zone.etichetta("ASSUNZIONE");
    private static final Pattern ANZ_CONV = Zone.etichetta("ANZ. CONV.");
    private static final Pattern FINE_RAPPORTO = Zone.etichetta("FINE RAPPORTO");
    private static final Pattern CENTRO_COSTO = Zone.etichetta("CENTRO DI COSTO");
    private static final Pattern SCATTI = Zone.etichetta("SCATTI ANZIANITA'");
    private static final Pattern LIVELLO = Zone.etichetta("LIVELLO");
    private static final Pattern PART_TIME = Zone.etichetta("% PART TIME");
    private static final Pattern RATEI = Zone.etichetta("RATEI");
    private static final Pattern RESIDUI_AP = Zone.etichetta("RESIDUI A.P.");
    private static final Pattern MATURATI = Zone.etichetta("MATURATI");
    private static final Pattern AP = Zone.etichetta("A.P.");
    private static final Pattern AC = Zone.etichetta("A.C.");
    private static final Pattern TOTALI = Zone.etichetta("TOTALI");
    private static final Pattern MINIMO = Zone.etichetta("MINIMO");
    private static final Pattern RETRIBUZIONE_ORARIA = Zone.etichetta("RETRIBUZIONE ORARIA");
    private static final Pattern RETRIBUZIONE_GIORNALIERA = Zone.etichetta("RETRIBUZIONE GIORNALIERA");
    private static final Pattern RETRIBUZIONE_MENSILE = Zone.etichetta("RETRIBUZIONE MENSILE");
    private static final Pattern QUANTITA = Zone.etichetta("Quantita'");
    private static final Pattern DESCRIZIONE = Zone.etichetta("DESCRIZIONE");
    private static final Pattern BASE = Zone.etichetta("Base");
    private static final Pattern TRATTENUTE = Zone.etichetta("TRATTENUTE");
    private static final Pattern COMPETENZE = Zone.etichetta("COMPETENZE");
    private static final Pattern UNITA = Zone.etichetta("Unita'");
    private static final Pattern ELEMENTI_VARIABILI = Zone.etichetta("Gli elementi variabili");
    private static final Pattern CAUSALE = Zone.etichetta("Causale presenze/assenze");
    private static final Pattern SONO_DI = Pattern.compile("SONODI(\\d{1,2}/\\d{4})");
    private static final Pattern DESCRIZIONE_CONTRIBUTO = Zone.etichetta("DESCRIZIONE CONTRIBUTO");
    private static final Pattern IMPONIBILE = Zone.etichetta("IMPONIBILE");
    private static final Pattern PERCENTUALE = Zone.etichetta("% C/DIP");
    private static final Pattern C_DIPENDENTE = Zone.etichetta("C/DIPENDENTE");
    private static final Pattern C_DITTA = Zone.etichetta("C/DITTA:");
    private static final Pattern SETT_RETR = Zone.etichetta("SETT. RETR.");
    private static final Pattern GG_RETR = Zone.etichetta("GG. RETR.");
    private static final Pattern GG_LAV = Zone.etichetta("GG. LAV.");
    private static final Pattern ORE_LAV = Zone.etichetta("ORE LAV.");
    private static final Pattern IMPONIBILE_CONTRIBUTIVO_ANNO = Zone.etichetta("IMPON.CONTRIBUTIVO ANNO");
    private static final Pattern CONTRIBUTI_ANNO = Zone.etichetta("CONTRIBUTI ANNO");
    private static final Pattern IMPONIBILE_CONTRIBUTIVO_MESE = Zone.etichetta("IMPON.CONTRIBUTIVO MESE");
    private static final Pattern IMPONIBILE_ARROTONDATO = Zone.etichetta("IMPON.CONTRIB. ARROT. MESE");
    private static final Pattern TOTALE_CONTRIBUTI = Zone.etichetta("TOTALE CONTRIBUTI");
    private static final Pattern IMPONIBILE_FISCALE = Zone.etichetta("IMPONIBILE FISCALE");
    private static final Pattern IRPEF_LORDA = Zone.etichetta("IRPEF LORDA");
    private static final Pattern IRPEF_SOSTITUTIVA = Zone.etichetta("IRPEF + IMP. SOST.");
    private static final Pattern IRPEF_NETTA = Zone.etichetta("IRPEF NETTA");
    private static final Pattern IRPEF_TRATTENUTA = Zone.etichetta("IRPEF TRATTENUTA");
    private static final Pattern IRPEF_CONGUAGLIO = Zone.etichetta("IRPEF CONGUAGLIO");
    private static final Pattern IMPOSTA_SOSTITUTIVA = Zone.etichetta("IMPOSTA SOSTITUTIVA");
    private static final Pattern CONGUAGLIO = Zone.etichetta("CONG.IRPEF+IMP.SOST.");
    private static final Pattern RETRIBUZIONE_UTILE_TFR = Zone.etichetta("RETRIBUZIONE UTILE TFR");
    private static final Pattern CONTRIBUTO_AGGIUNTIVO_TFR = Zone.etichetta("CONTR. AGG. TFR");
    private static final Pattern TFR_MESE = Zone.etichetta("TFR MESE");
    private static final Pattern TFR_ANNUO = Zone.etichetta("TFR ANNUO PROGR.");
    private static final Pattern FONDO_TFR = Zone.etichetta("F.DO TFR 31/12 AP");
    private static final Pattern ANTICIPAZIONI = Zone.etichetta("ANTICIPAZIONI ANNO");
    private static final Pattern TFR_PENSIONE = Zone.etichetta("TFR A F.DO PENSIONE");
    private static final Pattern TOTALE_COMPETENZE = Zone.etichetta("TOTALE COMPETENZE");
    private static final Pattern TOTALE_TRATTENUTE = Zone.etichetta("TOTALE TRATTENUTE");
    private static final Pattern ARR_PRECEDENTE = Zone.etichetta("ARR. PRECED.");
    private static final Pattern ARR_ATTUALE = Zone.etichetta("ARR. ATTUALE");
    private static final Pattern QUOTA_TFR_FONDI = Pattern.compile("QUOTA\\s*TFR.*(TESORERIA|FONDO|F\\.DO|PREV)");
    private static final float MAX = Float.MAX_VALUE;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String nome() {
        return "JOB Sistemi";
    }

    @Override
    public boolean riconosce(DocumentoTesto documento) {
        var testo = documento.compatto();
        return testo.contains("MESEDIRETRIBUZIONE") && testo.contains("NETTOINBUSTA")
                && testo.contains("TFRSPETTANTEAZIENDA");
    }

    @Override
    public CedolinoLetto leggi(DocumentoTesto documento) {
        var pagine = documento.pagine().stream()
                .filter(p -> p.prima(MESE_RETRIBUZIONE).isPresent() && p.prima(NETTO_IN_BUSTA).isPresent())
                .toList();
        if (pagine.isEmpty()) {
            pagine = List.of(documento.pagine().getFirst());
        }
        var cedolino = leggi(pagine.getFirst());
        if (pagine.size() > 1) {
            var distinti = new HashSet<String>();
            for (var p : pagine) {
                var letto = p == pagine.getFirst() ? cedolino : leggi(p);
                distinti.add(letto.codiceFiscale + "|" + letto.periodo + "|" + letto.intestazioneTredicesima
                        + letto.intestazioneQuattordicesima);
            }
            cedolino.numeroCedolini = distinti.size();
        }
        return cedolino;
    }

    private CedolinoLetto leggi(Pagina p) {
        var c = new CedolinoLetto();
        azienda(p, c);
        periodo(p, c);
        dipendente(p, c);
        contratto(p, c);
        float intestazioneVoci = voci(p, c);
        float limiteSuperiore = Float.isNaN(intestazioneVoci) ? p.altezza() : intestazioneVoci;
        ratei(p, c, limiteSuperiore);
        elementi(p, c, limiteSuperiore);
        contributi(p, c);
        riquadroContributivo(p, c);
        fisco(p, c);
        tfr(p, c);
        totali(p, c);
        c.controllaSomme();
        return c;
    }

    // ------------------------------------------------------------------------------------------- intestazione

    private static void azienda(Pagina p, CedolinoLetto c) {
        var inps = p.prima(POS_INPS);
        if (inps.isEmpty()) {
            c.etichettaMancante("POS. INPS");
            return;
        }
        var e = inps.get();
        Zone.stessaRiga(p, e, e.x0() - 50, e.x0() - 1).stream()
                .filter(w -> w.testo().matches("\\d{1,8}"))
                .reduce((a, b) -> b)
                .ifPresent(w -> c.codiceAzienda = w.testo());
        Zone.primaSotto(p, e, 12, e.x0() - 5, e.x1() + 40, t -> t.matches("\\d{6,}[0-9/A-Z]*"))
                .ifPresent(w -> c.posizioneInps = w.testo());
        var cf = p.prima(CF_AZIENDA);
        float fine = cf.map(Etichetta::y).orElse(e.y() + 45);
        var righe = new ArrayList<String>();
        for (var r : p.righe(e.y() + 3, fine - 3)) {
            var testo = Zone.testo(r.parole().stream().filter(w -> w.centro() < e.x0() - 8).toList());
            if (!testo.isEmpty()) {
                righe.add(testo);
            }
        }
        if (!righe.isEmpty()) {
            c.ragioneSociale = righe.getFirst();
            if (righe.size() > 1) {
                c.indirizzoAzienda = String.join(", ", righe.subList(1, righe.size()));
            }
        }
        cf.flatMap(x -> Zone.aDestra(p, x, 80, t -> t.matches("\\d{11}|[A-Z0-9]{16}")))
                .ifPresent(w -> c.codiceFiscaleAzienda = w.testo());
        p.prima(PARTITA_IVA).flatMap(x -> Zone.aDestra(p, x, 80, t -> t.matches("\\d{11}")))
                .ifPresent(w -> c.partitaIva = w.testo());
        p.prima(POS_INAIL)
                .flatMap(x -> Zone.primaSotto(p, x, 12, x.x0() - 5, x.x1() + 15, t -> t.matches("\\d{5,}(/\\d+)?")))
                .ifPresent(w -> c.posizioneInail = w.testo());
    }

    private static void periodo(Pagina p, CedolinoLetto c) {
        p.prima(STAMPATO)
                .flatMap(e -> Zone.aDestra(p, e, 60, t -> Valori.leggiData(t).isPresent()))
                .flatMap(w -> Valori.leggiData(w.testo()))
                .ifPresent(d -> c.dataElaborazione = d);
        var etichetta = p.prima(MESE_RETRIBUZIONE);
        if (etichetta.isEmpty()) {
            c.etichettaMancante("MESE DI RETRIBUZIONE");
            return;
        }
        var e = etichetta.get();
        var testo = Zone.testo(Zone.primaRigaSotto(p, e, 12, e.x0() - 20, e.x1() + 140));
        Periodo.leggi(testo, c);
    }

    private static void dipendente(Pagina p, CedolinoLetto c) {
        var etichetta = p.prima(CODICE_FISCALE);
        if (etichetta.isEmpty()) {
            c.etichettaMancante("CODICE FISCALE");
            return;
        }
        var e = etichetta.get();
        var nato = Zone.sullaRiga(p, NATO_A, e.y(), Zone.STESSA_LINEA, e.x1());
        float fineCodice = nato.map(Etichetta::x0).orElse(e.x1() + 60);
        Zone.primaSotto(p, e, 14, e.x0() - 5, fineCodice + 20, Valori::codiceFiscale)
                .ifPresent(w -> c.codiceFiscale = w.testo());
        var cognomeNome = Zone.sullaRiga(p, COGNOME_NOME, e.y(), Zone.STESSA_LINEA, -MAX);
        Zone.sullaRiga(p, COD_DIP, e.y(), Zone.STESSA_LINEA, -MAX).ifPresent(k -> Zone.primaSotto(p, k, 14, k.x0() - 5,
                        cognomeNome.map(Etichetta::x0).orElse(k.x1() + 10) - 2, t -> t.matches("\\d{1,10}"))
                .ifPresent(w -> c.codiceDipendente = w.testo()));
        cognomeNome.ifPresent(n -> {
            var parole = riga(Zone.sotto(p, n, 14, -MAX, MAX)).stream()
                    .filter(w -> w.x0() >= n.x0() - 3 && w.x0() < e.x0() - 3)
                    .toList();
            var divisi = Classificazione.cognomeNome(Zone.testo(parole), c.codiceFiscale);
            c.cognome = divisi[0];
            c.nome = divisi[1];
        });
        nato.flatMap(n -> Zone.sotto(p, n, 14, n.x0(), MAX).stream()
                        .map(w -> Valori.leggiData(w.testo()))
                        .flatMap(Optional::stream)
                        .findFirst())
                .ifPresent(d -> c.dataNascita = d);
    }

    private static void contratto(Pagina p, CedolinoLetto c) {
        p.prima(QUALIFICA).ifPresent(q -> {
            var contratto = Zone.sullaRiga(p, CONTRATTO, q.y(), Zone.STESSA_LINEA, q.x1());
            float fineQualifica = contratto.map(Etichetta::x0).orElse(q.x1() + 90) - 3;
            var valori = riga(Zone.sotto(p, q, 14, q.x0() - 3, MAX));
            var qualifica = Zone.testo(valori.stream().filter(w -> w.x0() >= q.x0() - 3 && w.x0() < fineQualifica)
                    .toList());
            c.qualifica = qualifica.isEmpty() ? null : qualifica;
            contratto.ifPresent(k -> {
                var numero = Zone.sullaRiga(p, NUMERO, k.y(), Zone.STESSA_LINEA, k.x1());
                float fineContratto = numero.map(Etichetta::x0).orElse(k.x1() + 140) - 3;
                var ccnl = Zone.testo(valori.stream().filter(w -> w.x0() >= k.x0() - 3 && w.x0() < fineContratto)
                        .toList());
                c.ccnl = ccnl.isEmpty() ? null : ccnl;
                var intestazione = Zone.testo(Zone.stessaRiga(p, k, k.x0(), fineContratto))
                        .replace(" ", "").toUpperCase(java.util.Locale.ROOT);
                var cnel = CNEL.matcher(intestazione);
                if (cnel.find()) {
                    c.codiceCnel = cnel.group(1);
                }
            });
        });
        if (c.ccnl == null) {
            c.etichettaMancante("CONTRATTO DI LAVORO");
        }
        p.prima(ASSUNZIONE).ifPresent(a -> {
            float fine = Zone.sullaRiga(p, ANZ_CONV, a.y(), Zone.STESSA_LINEA, a.x1()).map(Etichetta::x0).orElse(a.x1() + 15) - 2;
            data(p, a, a.x0() - 10, fine).ifPresent(d -> c.dataAssunzione = d);
            Zone.sullaRiga(p, FINE_RAPPORTO, a.y(), Zone.STESSA_LINEA, a.x1()).ifPresent(f -> {
                float fineCessazione = Zone.sullaRiga(p, CENTRO_COSTO, a.y(), Zone.STESSA_LINEA, f.x1()).map(Etichetta::x0)
                        .orElse(f.x1() + 15) - 2;
                data(p, f, f.x0() - 10, fineCessazione).ifPresent(d -> c.dataCessazione = d);
            });
        });
        p.prima(SCATTI).ifPresent(s -> {
            var livello = Zone.sullaRiga(p, LIVELLO, s.y(), Zone.STESSA_LINEA, s.x1());
            var parole = Zone.sotto(p, s, 14, s.x0() - 5, livello.map(Etichetta::x0).orElse(s.x1() + 120) - 3);
            for (int i = 0; i < parole.size(); i++) {
                var t = parole.get(i).testo();
                if (t.equals("N.") && i + 1 < parole.size()) {
                    Valori.leggiNumero(parole.get(i + 1).testo())
                            .filter(n -> n.signum() >= 0 && n.stripTrailingZeros().scale() <= 0)
                            .ifPresent(n -> c.numeroScatti = n.intValue());
                }
                Valori.leggiData(t).ifPresent(d -> c.dataProssimoScatto = d);
            }
            livello.ifPresent(l -> {
                float fine = Zone.sullaRiga(p, PART_TIME, l.y(), Zone.STESSA_LINEA, l.x1()).map(Etichetta::x0).orElse(l.x1() + 25) - 3;
                Zone.primaSotto(p, l, 14, l.x0() - 10, fine, t -> t.matches("[A-Za-z0-9]{1,4}"))
                        .ifPresent(w -> c.livello = w.testo());
            });
        });
    }

    private static Optional<java.time.LocalDate> data(Pagina p, Etichetta e, float x0, float x1) {
        return Zone.sotto(p, e, 14, x0, x1).stream()
                .map(w -> Valori.leggiData(w.testo()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Parole della stessa riga della prima. */
    private static List<Parola> riga(List<Parola> parole) {
        if (parole.isEmpty()) {
            return parole;
        }
        float y = parole.getFirst().y();
        return parole.stream().filter(w -> Math.abs(w.y() - y) <= DocumentoTesto.TOLLERANZA_RIGA).toList();
    }

    // ------------------------------------------------------------------------------------- ratei ed elementi

    private static void ratei(Pagina p, CedolinoLetto c, float yMax) {
        var etichetta = Zone.prima(p, RATEI, 0, yMax);
        if (etichetta.isEmpty()) {
            c.etichettaMancante("RATEI");
            return;
        }
        var e = etichetta.get();
        var residui = Zone.sullaRiga(p, RESIDUI_AP, e.y(), Zone.STESSA_LINEA, e.x1());
        var maturati = Zone.sullaRiga(p, MATURATI, e.y(), Zone.STESSA_LINEA, e.x1());
        if (residui.isEmpty() || maturati.isEmpty()) {
            c.etichettaMancante("colonne dei ratei");
            return;
        }
        float dopoMaturati = maturati.get().x1();
        var colonne = new Colonne<String>()
                .colonna("residuo", residui.get())
                .colonna("maturato", maturati.get());
        p.cerca(AP).stream().filter(x -> x.y() > e.y() - 2 && x.y() < e.y() + 8 && x.x0() > dopoMaturati)
                .findFirst().ifPresent(x -> colonne.colonna("godutoAP", x));
        p.cerca(AC).stream().filter(x -> x.y() > e.y() - 2 && x.y() < e.y() + 8 && x.x0() > dopoMaturati)
                .findFirst().ifPresent(x -> colonne.colonna("godutoAC", x));
        var totali = p.cerca(TOTALI).stream()
                .filter(x -> x.y() > e.y() - 2 && x.y() < e.y() + 8 && x.x0() > dopoMaturati)
                .findFirst();
        totali.ifPresent(x -> colonne.colonna("saldo", x));
        float limite = Zone.prima(p, MINIMO, e.y() - 10, e.y() + 10).map(m -> m.x0() - 3)
                .orElse(totali.map(t -> t.x1() + 30).orElse(e.x0() + 260));
        float fine = Zone.prima(p, RETRIBUZIONE_ORARIA, e.y(), yMax).map(x -> x.y() - 3).orElse(e.y() + 70);
        float inizioValori = residui.get().x0() - 30;
        for (var riga : p.righe(e.y() + 8, fine)) {
            var etichettaRiga = Zone.testo(riga.parole().stream()
                    .filter(w -> w.x1() < residui.get().x0() + 5 && !Valori.importo(w.testo()))
                    .toList());
            var tipo = Classificazione.tipoRateo(etichettaRiga);
            if (tipo.isEmpty()) {
                continue;
            }
            // i valori sono stampati con un altro carattere: linea di base fino a un paio di punti piu' in basso
            var valori = p.parole(new Area(inizioValori, limite, riga.y() - 1.5f, riga.y() + Zone.STESSA_LINEA + 1),
                    Zone.IMPORTO);
            if (valori.isEmpty()) {
                continue;
            }
            var assegnati = colonne.assegna(valori, 25);
            var unita = Classificazione.unitaRateo(etichettaRiga.replaceAll(".*\\(", "("));
            if (unita.isEmpty()) {
                unita = Classificazione.unitaRateo(Zone.testo(p.parole(new Area(-MAX, inizioValori, riga.y() + 3,
                        riga.y() + 10))));
            }
            c.ratei.add(new Rateo(tipo.get(), unita.orElse(unitaPredefinita(tipo.get())),
                    Zone.oZero(Zone.importo(assegnati, "residuo")),
                    Zone.oZero(Zone.importo(assegnati, "maturato")),
                    Zone.oZero(Zone.importo(assegnati, "godutoAP")).add(Zone.oZero(Zone.importo(assegnati, "godutoAC"))),
                    Zone.oZero(Zone.importo(assegnati, "saldo"))));
        }
    }

    static UnitaMisura unitaPredefinita(TipoRateo tipo) {
        return tipo == TipoRateo.FERIE ? UnitaMisura.GIORNI : UnitaMisura.ORE;
    }

    private static void elementi(Pagina p, CedolinoLetto c, float yMax) {
        Zone.prima(p, MINIMO, 0, yMax).ifPresentOrElse(m -> {
            var gruppi = Zone.gruppi(p, m.y(), m.x0() - 1, p.larghezza(), 6);
            var colonne = new LinkedHashMap<Zone.Gruppo, Etichetta>();
            gruppi.forEach(g -> colonne.put(g, g.etichetta()));
            var valori = Zone.valoriSotto(p, colonne, 12, p.larghezza(), Zone.IMPORTO);
            valori.forEach((g, w) -> Periodo.elemento(c, g.compatto(), Valori.leggiImporto(w.testo()).orElseThrow()));
        }, () -> c.etichettaMancante("MINIMO"));
        p.prima(RETRIBUZIONE_ORARIA).ifPresent(o -> {
            var colonne = new LinkedHashMap<String, Etichetta>();
            colonne.put("oraria", o);
            Zone.sullaRiga(p, RETRIBUZIONE_GIORNALIERA, o.y(), Zone.STESSA_LINEA, o.x1()).ifPresent(e -> colonne.put("giornaliera", e));
            Zone.sullaRiga(p, RETRIBUZIONE_MENSILE, o.y(), Zone.STESSA_LINEA, o.x1()).ifPresent(e -> colonne.put("mensile", e));
            var valori = Zone.valoriSotto(p, colonne, 14, p.larghezza(), Zone.IMPORTO);
            c.pagaOraria = Zone.importo(valori, "oraria");
            c.pagaGiornaliera = Zone.importo(valori, "giornaliera");
            c.totaleRetribuzione = Zone.importo(valori, "mensile");
        });
    }

    // ---------------------------------------------------------------------------------------------------- voci

    /** Legge le voci e restituisce la linea di base della loro intestazione (NaN se assente). */
    private static float voci(Pagina p, CedolinoLetto c) {
        var quantita = p.prima(QUANTITA);
        if (quantita.isEmpty()) {
            c.etichettaMancante("intestazione delle voci");
            return Float.NaN;
        }
        var q = quantita.get();
        float y = q.y();
        var descrizione = Zone.sullaRiga(p, DESCRIZIONE, y, Zone.STESSA_LINEA, -MAX);
        var base = Zone.sullaRiga(p, BASE, y, Zone.STESSA_LINEA, q.x1());
        var trattenute = Zone.sullaRiga(p, TRATTENUTE, y, Zone.STESSA_LINEA, q.x1());
        var competenze = Zone.sullaRiga(p, COMPETENZE, y, Zone.STESSA_LINEA, q.x1());
        if (descrizione.isEmpty() || base.isEmpty() || trattenute.isEmpty() || competenze.isEmpty()) {
            c.etichettaMancante("colonne delle voci");
            return y;
        }
        var d = descrizione.get();
        var b = base.get();
        var t = trattenute.get();
        var k = competenze.get();
        var unita = Zone.prima(p, UNITA, y - 5, y + 1);
        var colonnaNetto = p.parole(new Area(k.x1(), p.larghezza(), y - 5, y - 0.5f)).stream()
                .filter(w -> w.testo().equals("N"))
                .findFirst();
        float fine = p.altezza();
        for (var marcatore : List.of(ELEMENTI_VARIABILI, CAUSALE, DESCRIZIONE_CONTRIBUTO)) {
            var trovato = Zone.prima(p, marcatore, y + 1, p.altezza());
            if (trovato.isPresent()) {
                fine = Math.min(fine, trovato.get().y());
            }
        }
        float limiteDescrizione = unita.map(u -> u.x0() - 8).orElse(q.x0() - 50);
        float sogliaColonne = (t.centro() + k.centro()) / 2;
        for (var riga : p.righe(y + 5, fine - 1)) {
            String codice = null;
            String unitaVoce = null;
            var testo = new ArrayList<String>();
            BigDecimal qta = null;
            BigDecimal valoreBase = null;
            BigDecimal competenza = null;
            BigDecimal trattenuta = null;
            boolean flagNetto = false;
            for (var w : riga.parole()) {
                var parola = w.testo();
                if (parola.chars().allMatch(ch -> ch == '*')) {
                    if (colonnaNetto.isPresent() && Math.abs(w.centro() - colonnaNetto.get().centro()) <= 4) {
                        flagNetto = true;
                    }
                    continue;
                }
                if (codice == null && testo.isEmpty() && w.x1() <= d.x0() - 1 && parola.matches("\\d{1,6}")) {
                    codice = parola;
                    continue;
                }
                if (Valori.importo(parola)) {
                    var v = Valori.leggiImporto(parola).orElseThrow();
                    float x = w.centro();
                    if (x < b.x0() - 3) {
                        qta = v;
                    } else if (x < t.x0() - 3) {
                        valoreBase = v;
                    } else if (x < sogliaColonne) {
                        trattenuta = v;
                    } else if (x <= k.x1() + 15) {
                        competenza = v;
                    }
                    continue;
                }
                if (w.x0() >= limiteDescrizione && w.x1() <= q.x0() + 4) {
                    unitaVoce = parola;
                } else if (w.x0() < limiteDescrizione) {
                    testo.add(parola);
                }
            }
            if (codice == null && qta == null && valoreBase == null && competenza == null && trattenuta == null) {
                continue;
            }
            var descrizioneVoce = String.join(" ", testo);
            Boolean nelNetto = colonnaNetto.isPresent() ? flagNetto : null;
            c.voce(codice, descrizioneVoce, qta, Classificazione.unitaVoce(unitaVoce), valoreBase, competenza,
                    trattenuta, nelNetto);
            vociSpeciali(c, descrizioneVoce, qta, valoreBase, competenza, trattenuta);
        }
        for (var riga : p.righe(y, Math.min(p.altezza(), fine + 30))) {
            var m = SONO_DI.matcher(riga.compatta());
            if (m.find()) {
                Valori.leggiMeseAnno(m.group(1)).ifPresent(v -> c.meseVariabili = v);
            }
        }
        return y;
    }

    private static void vociSpeciali(CedolinoLetto c, String descrizione, BigDecimal quantita, BigDecimal base,
                                      BigDecimal competenza, BigDecimal trattenuta) {
        var d = Classificazione.maiuscolo(descrizione);
        if (QUOTA_TFR_FONDI.matcher(d).find() && c.tfrQuotaFondi == null) {
            c.tfrQuotaFondi = competenza != null ? competenza : trattenuta;
        }
        if (Classificazione.buoniPasto(d) && c.buoniPastoImporto == null) {
            c.buoniPastoNumero = quantita;
            c.buoniPastoValore = base;
            c.buoniPastoImporto = competenza != null ? competenza : trattenuta;
        }
    }

    // -------------------------------------------------------------------------------- contributi e presenze

    private static void contributi(Pagina p, CedolinoLetto c) {
        var intestazioni = p.cerca(DESCRIZIONE_CONTRIBUTO);
        if (intestazioni.isEmpty()) {
            c.etichettaMancante("DESCRIZIONE CONTRIBUTO");
            return;
        }
        float y = intestazioni.getFirst().y();
        var meta = intestazioni.stream().filter(e -> Math.abs(e.y() - y) <= 2)
                .sorted(java.util.Comparator.comparingDouble(Etichetta::x0)).toList();
        float fine = Zone.prima(p, SETT_RETR, y, p.altezza()).map(e -> e.y() - 2).orElse(y + 60);
        for (int i = 0; i < meta.size(); i++) {
            var h = meta.get(i);
            float x1 = i + 1 < meta.size() ? meta.get(i + 1).x0() - 2 : p.larghezza();
            var colonne = new LinkedHashMap<String, Etichetta>();
            Zone.sullaRiga(p, IMPONIBILE, y, Zone.STESSA_LINEA, h.x1()).filter(e -> e.x0() < x1).ifPresent(e -> colonne.put("imp", e));
            Zone.sullaRiga(p, PERCENTUALE, y, Zone.STESSA_LINEA, h.x1()).filter(e -> e.x0() < x1).ifPresent(e -> colonne.put("perc", e));
            Zone.sullaRiga(p, C_DIPENDENTE, y, Zone.STESSA_LINEA, h.x1()).filter(e -> e.x0() < x1).ifPresent(e -> colonne.put("dip", e));
            Zone.sullaRiga(p, C_DITTA, y, Zone.STESSA_LINEA, h.x1()).filter(e -> e.x0() < x1).ifPresent(e -> colonne.put("ditta", e));
            if (!colonne.containsKey("imp") || !colonne.containsKey("dip")) {
                c.etichettaMancante("colonne dei contributi");
                continue;
            }
            float inizioImponibile = colonne.get("imp").x0();
            for (var riga : p.righe(y + 3, fine)) {
                var parole = riga.parole().stream().filter(w -> w.x0() >= h.x0() - 3 && w.centro() < x1).toList();
                var descrizione = Zone.testo(parole.stream()
                        .filter(w -> w.x0() < inizioImponibile - 2 && !Valori.importo(w.testo())).toList());
                var numeri = parole.stream().filter(w -> Valori.importo(w.testo())).toList();
                if (descrizione.isEmpty() || numeri.isEmpty()) {
                    continue;
                }
                var valori = Zone.perInizio(colonne, numeri, 4);
                var imponibile = Zone.importo(valori, "imp");
                var percentuale = Zone.importo(valori, "perc");
                var dipendente = Zone.importo(valori, "dip");
                var ditta = Zone.importo(valori, "ditta");
                var unita = percentuale == null ? null : "%";
                if (dipendente != null) {
                    c.voce(null, descrizione, percentuale, unita, imponibile, null, dipendente, null);
                } else if (ditta != null || imponibile != null) {
                    c.informativa(null, descrizione, percentuale, unita, ditta != null ? ditta : imponibile);
                }
            }
        }
    }

    private static void riquadroContributivo(Pagina p, CedolinoLetto c) {
        p.prima(SETT_RETR).ifPresentOrElse(s -> {
            var pattern = new LinkedHashMap<String, Pattern>();
            pattern.put("sett", SETT_RETR);
            pattern.put("ggRetr", GG_RETR);
            pattern.put("ggLav", GG_LAV);
            pattern.put("oreLav", ORE_LAV);
            pattern.put("impAnno", IMPONIBILE_CONTRIBUTIVO_ANNO);
            pattern.put("contrAnno", CONTRIBUTI_ANNO);
            pattern.put("impMese", IMPONIBILE_CONTRIBUTIVO_MESE);
            pattern.put("impArrot", IMPONIBILE_ARROTONDATO);
            pattern.put("totContr", TOTALE_CONTRIBUTI);
            var colonne = Zone.etichette(p, s.y(), Zone.STESSA_LINEA, pattern);
            var valori = Zone.valoriSotto(p, colonne, 14, p.larghezza(), Zone.NUMERO);
            c.giorniRetribuiti = Zone.importo(valori, "ggRetr");
            c.giorniLavorati = Zone.importo(valori, "ggLav");
            c.oreLavorate = Zone.importo(valori, "oreLav");
            c.imponibilePrevidenziale = Zone.importo(valori, "impAnno");
        }, () -> c.etichettaMancante("SETT. RETR."));
    }

    // ------------------------------------------------------------------------------------- fisco, TFR, totali

    private static void fisco(Pagina p, CedolinoLetto c) {
        p.prima(IRPEF_SOSTITUTIVA).ifPresent(e -> Zone.primaSotto(p, e, 14, e.x0() - 20, e.x1() + 30, Zone.IMPORTO)
                .flatMap(w -> Valori.leggiImporto(w.testo()))
                .ifPresent(v -> c.voce(null, "IRPEF + IMP. SOST.", null, null, null, null, v, null)));
        var imponibili = p.cerca(IMPONIBILE_FISCALE);
        if (imponibili.size() >= 2) {
            var annuale = imponibili.get(1);
            var colonne = new LinkedHashMap<String, Etichetta>();
            colonne.put("imponibile", annuale);
            Zone.sullaRiga(p, IRPEF_LORDA, annuale.y(), Zone.STESSA_LINEA, annuale.x1()).ifPresent(e -> colonne.put("lorda", e));
            c.imponibileIrpef = Zone.importo(Zone.valoriSotto(p, colonne, 14, p.larghezza(), Zone.IMPORTO),
                    "imponibile");
        }
        p.prima(IRPEF_TRATTENUTA).ifPresent(t -> {
            var colonne = new LinkedHashMap<String, Etichetta>();
            Zone.sullaRiga(p, IRPEF_NETTA, t.y(), Zone.STESSA_LINEA, -MAX).ifPresent(e -> colonne.put("netta", e));
            colonne.put("trattenuta", t);
            Zone.sullaRiga(p, IRPEF_CONGUAGLIO, t.y(), Zone.STESSA_LINEA, t.x1()).ifPresent(e -> colonne.put("conguaglio", e));
            Zone.sullaRiga(p, IMPOSTA_SOSTITUTIVA, t.y(), Zone.STESSA_LINEA, t.x1()).ifPresent(e -> colonne.put("sostitutiva", e));
            Zone.sullaRiga(p, CONGUAGLIO, t.y(), Zone.STESSA_LINEA, t.x1()).ifPresent(e -> colonne.put("totale", e));
            var valori = Zone.valoriSotto(p, colonne, 18, p.larghezza(), Zone.IMPORTO);
            c.irpefTrattenuta = Zone.importo(valori, "trattenuta");
            var conguaglio = Zone.importo(valori, "totale");
            if (conguaglio != null) {
                c.voce(null, "CONG.IRPEF+IMP.SOST.", null, null, null, null, conguaglio, null);
            }
        });
    }

    private static void tfr(Pagina p, CedolinoLetto c) {
        p.prima(RETRIBUZIONE_UTILE_TFR).ifPresentOrElse(r -> {
            var pattern = new LinkedHashMap<String, Pattern>();
            pattern.put("utile", RETRIBUZIONE_UTILE_TFR);
            pattern.put("aggiuntivo", CONTRIBUTO_AGGIUNTIVO_TFR);
            pattern.put("mese", TFR_MESE);
            pattern.put("anno", TFR_ANNUO);
            pattern.put("fondo", FONDO_TFR);
            pattern.put("anticipi", ANTICIPAZIONI);
            pattern.put("spettante", TFR_SPETTANTE);
            pattern.put("pensione", TFR_PENSIONE);
            var colonne = Zone.etichette(p, r.y(), Zone.STESSA_LINEA, pattern);
            var valori = Zone.valoriSotto(p, colonne, 14, p.larghezza(), Zone.IMPORTO);
            c.tfrRetribuzioneUtile = Zone.importo(valori, "utile");
            c.tfrQuotaMese = Zone.importo(valori, "mese");
            c.tfrQuotaAnno = Zone.importo(valori, "anno");
            c.tfrFondo = Zone.importo(valori, "fondo");
            c.tfrAnticipi = Zone.importo(valori, "anticipi");
            var pensione = Zone.importo(valori, "pensione");
            if (pensione != null) {
                c.tfrQuotaFondi = pensione;
            }
            var spettante = Zone.importo(valori, "spettante");
            if (spettante != null && pensione == null && c.tfrAnticipi == null) {
                var atteso = Zone.oZero(c.tfrFondo).add(Zone.oZero(c.tfrQuotaAnno));
                c.tfrCoerente = atteso.subtract(spettante).abs().compareTo(CedolinoLetto.TOLLERANZA_SOMME) <= 0;
                if (!c.tfrCoerente) {
                    c.controlloFallito("Il TFR spettante non corrisponde al fondo al 31/12 piu' la quota dell'anno");
                }
            }
        }, () -> c.etichettaMancante("RETRIBUZIONE UTILE TFR"));
    }

    private static void totali(Pagina p, CedolinoLetto c) {
        var competenze = p.prima(TOTALE_COMPETENZE);
        if (competenze.isEmpty()) {
            c.etichettaMancante("TOTALE COMPETENZE");
            return;
        }
        var pattern = new LinkedHashMap<String, Pattern>();
        pattern.put("competenze", TOTALE_COMPETENZE);
        pattern.put("trattenute", TOTALE_TRATTENUTE);
        pattern.put("precedente", ARR_PRECEDENTE);
        pattern.put("attuale", ARR_ATTUALE);
        pattern.put("netto", NETTO_IN_BUSTA);
        var colonne = Zone.etichette(p, competenze.get().y(), Zone.STESSA_LINEA, pattern);
        var valori = Zone.valoriSotto(p, colonne, 16, p.larghezza(), Zone.IMPORTO);
        c.totaleCompetenze = Zone.importo(valori, "competenze");
        c.lordo = c.totaleCompetenze;
        c.totaleTrattenute = Zone.importo(valori, "trattenute");
        c.netto = Zone.importo(valori, "netto");
        var precedente = Zone.importo(valori, "precedente");
        var attuale = Zone.importo(valori, "attuale");
        if (precedente != null || attuale != null) {
            c.arrotondamento = Zone.oZero(attuale).subtract(Zone.oZero(precedente));
        }
    }
}
