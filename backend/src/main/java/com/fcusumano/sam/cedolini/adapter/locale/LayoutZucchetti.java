package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.adapter.locale.CedolinoLetto.Rateo;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Area;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Etichetta;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Pagina;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Parola;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cedolino Zucchetti ("Modello per procedure ZUCCHETTI"), nelle due grafiche viste:
 * <ul>
 *   <li>classica: carattere a spaziatura fissa, campi adiacenti stampati attaccati (codice azienda e ragione sociale,
 *       posizione INPS e PAT INAIL, date di nascita e assunzione come blocchi di 8 cifre) che si separano con i
 *       limiti delle colonne delle etichette;</li>
 *   <li>recente: etichette con una "s" al posto degli spazi ("CodicesAzienda", "NETTOsDELsMESE") e date con il
 *       trattino.</li>
 * </ul>
 * Voci con le colonne Importo base / Riferimento / Trattenute / Competenze; progressivi, TFR e ratei in fondo, totali
 * e netto a destra.
 */
final class LayoutZucchetti implements LayoutCedolino {

    static final String ID = "zucchetti";

    private static final Pattern RICONOSCI_PERIODO = Pattern.compile("PERIODOS?DIS?RETRIBUZIONE");
    private static final Pattern RICONOSCI_NETTO = Pattern.compile("NETTOS?DELS?MESE");
    private static final Pattern CODICE_AZIENDA = z("CODICE AZIENDA");
    private static final Pattern RAGIONE_SOCIALE = z("RAGIONE SOCIALE");
    private static final Pattern INDIRIZZO = Pattern.compile("INDIRIZZO:?");
    private static final Pattern CODICE_FISCALE = z("CODICE FISCALE");
    private static final Pattern POSIZIONE_INPS = z("POSIZIONE INPS");
    private static final Pattern PAT_INAIL = z("P.A.T. INAIL");
    private static final Pattern UNITA_LOCALE = z("UNITA' LOCALE");
    private static final Pattern FILIALE = z("FILIALE/DIPENDENZA");
    private static final Pattern PERIODO = z("PERIODO DI RETRIBUZIONE");
    private static final Pattern VARIABILI = z("VARIABILI DI");
    private static final Pattern CODICE_DIPENDENTE = z("CODICE DIPENDENTE");
    private static final Pattern COGNOME_NOME = z("COGNOME E NOME");
    private static final Pattern MATRICOLA = Pattern.compile("S?MATRICOLA");
    private static final Pattern DATA_NASCITA = z("DATA DI NASCITA");
    private static final Pattern DATA_ASSUNZIONE = z("DATA ASSUNZIONE");
    private static final Pattern DATA_CESSAZIONE = z("DATA CESSAZIONE");
    private static final Pattern RIGA_LIVELLO = Pattern.compile("(?i)^(.*?)\\s*(\\S+?)['’]?\\s+livello\\b.*$");
    private static final Pattern ORE_ORDINARIE = z("ORE ORDINARIE");
    private static final Pattern MINIMO = z("MINIMO");
    private static final Pattern TOTALE = z("TOTALE");
    private static final Pattern PROSSIMO_SCATTO = Pattern.compile("PROSSIMO(S?SCATTO)?");
    private static final Pattern IMPORTO_BASE = z("IMPORTO BASE");
    private static final Pattern RIFERIMENTO = z("RIFERIMENTO");
    private static final Pattern TRATTENUTE = z("TRATTENUTE");
    private static final Pattern COMPETENZE = z("COMPETENZE");
    private static final Pattern CONGUAGLIO = z("CONGUAGLIO");
    private static final Pattern PROGRESSIVI = z("PROGRESSIVI");
    private static final Pattern IMP_INPS = z("IMP. INPS");
    private static final Pattern IMP_INAIL = z("IMP. INAIL");
    private static final Pattern IMP_IRPEF = z("IMP. IRPEF");
    private static final Pattern IRPEF_PAGATA = z("IRPEF PAGATA");
    private static final Pattern FONDO_TFR = z("F.DO 31/12");
    private static final Pattern RIVALUTAZIONE = z("RIVALUTAZ.");
    private static final Pattern IMPOSTA_RIVALUTAZIONE = z("IMP.RIVAL.");
    private static final Pattern QUOTA_ANNO = z("QUOTA ANNO");
    private static final Pattern TFR_FONDI = z("TFR A FONDI");
    private static final Pattern TFR_QUIR = z("TFR A QUIR");
    private static final Pattern ANTICIPI = z("ANTICIPI");
    private static final Pattern RATEI = z("RATEI");
    private static final Pattern RESIDUO = z("RESIDUO AP");
    private static final Pattern MATURATO = z("MATURATO");
    private static final Pattern GODUTO = z("GODUTO");
    private static final Pattern SALDO = z("SALDO");
    private static final Pattern TOTALE_COMPETENZE = z("TOTALE COMPETENZE");
    private static final Pattern TOTALE_TRATTENUTE = z("TOTALE TRATTENUTE");
    private static final Pattern ARROTONDAMENTO = z("ARROTONDAMENTO");
    private static final Pattern NETTO = z("NETTO DEL MESE");
    private static final Pattern CODICE_VOCE = Pattern.compile("\\**[A-Z]{0,2}\\d{3,6}");
    private static final Pattern RETRIBUZIONE_UTILE_TFR = Pattern.compile("RETRIBUZIONE\\s+UTILE\\s+T\\.?F\\.?R");
    private static final Pattern QUOTA_TFR = Pattern.compile("^QUOTA\\s+T\\.?F\\.?R\\.?$");
    private static final Pattern CODICE_FISCALE_AZIENDA = Pattern.compile("\\b(\\d{11}|[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z])\\b");
    private static final Pattern POSIZIONE = Pattern.compile("\\d{5,10}(/\\d{1,3})?");
    private static final float MAX = Float.MAX_VALUE;

    private static Pattern z(String etichetta) {
        return Zone.etichetta(etichetta, "S?");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String nome() {
        return "Zucchetti";
    }

    @Override
    public boolean riconosce(DocumentoTesto documento) {
        var testo = documento.compatto();
        return testo.contains("ZUCCHETTI") && RICONOSCI_PERIODO.matcher(testo).find()
                && RICONOSCI_NETTO.matcher(testo).find();
    }

    @Override
    public CedolinoLetto leggi(DocumentoTesto documento) {
        var pagine = documento.pagine().stream().filter(p -> p.prima(PERIODO).isPresent()).toList();
        if (pagine.isEmpty()) {
            pagine = List.of(documento.pagine().getFirst());
        }
        var cedolino = leggi(pagine.getFirst());
        if (pagine.size() > 1) {
            var distinti = new HashSet<String>();
            for (var p : pagine) {
                var letto = p == pagine.getFirst() ? cedolino : leggi(p);
                distinti.add(letto.codiceFiscale + "|" + letto.periodo + "|" + letto.intestazioneAggiuntiva);
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
        float inizioVoci = voci(p, c);
        elementi(p, c, Float.isNaN(inizioVoci) ? p.altezza() : inizioVoci);
        progressivi(p, c);
        ratei(p, c);
        totali(p, c);
        c.controllaSomme();
        return c;
    }

    // ------------------------------------------------------------------------------------------- intestazione

    private static void azienda(Pagina p, CedolinoLetto c) {
        float destra = p.larghezza() * 0.67f;
        var ca = p.prima(CODICE_AZIENDA);
        var rs = p.prima(RAGIONE_SOCIALE);
        if (ca.isPresent() && rs.isPresent()) {
            var a = ca.get();
            var r = rs.get();
            Zone.rigaSotto(p, a, 22, a.x0() - 8, destra).ifPresent(y -> {
                c.codiceAzienda = Zone.testoRiga(p, y, a.x0() - 8, r.x0() - 4.5f);
                c.ragioneSociale = Zone.testoRiga(p, y, r.x0() - 4.5f, destra);
            });
        } else {
            c.etichettaMancante("RAGIONE SOCIALE");
        }
        var codiciFiscali = p.cerca(CODICE_FISCALE);
        p.prima(INDIRIZZO).ifPresent(i -> {
            float fine = codiciFiscali.stream().filter(e -> e.y() > i.y()).findFirst().map(Etichetta::y)
                    .orElse(i.y() + 30);
            var righe = new ArrayList<String>();
            for (var r : p.righe(i.y() + 0.5f, fine - 1)) {
                var testo = Zone.testo(r.parole().stream().filter(w -> w.centro() < destra).toList());
                if (!testo.isEmpty()) {
                    righe.add(testo);
                }
            }
            c.indirizzoAzienda = righe.isEmpty() ? null : String.join(", ", righe);
        });
        if (codiciFiscali.isEmpty()) {
            c.etichettaMancante("CODICE FISCALE");
            return;
        }
        var e = codiciFiscali.getFirst();
        var inps = Zone.sullaRiga(p, POSIZIONE_INPS, e.y(), Zone.STESSA_LINEA, e.x1());
        var inail = Zone.sullaRiga(p, PAT_INAIL, e.y(), Zone.STESSA_LINEA, e.x1());
        var unita = Zone.sullaRiga(p, UNITA_LOCALE, e.y(), Zone.STESSA_LINEA, e.x1()).or(() -> Zone.sullaRiga(p, FILIALE, e.y(), 2,
                e.x1()));
        float fineCf = inps.map(x -> x.x0() - 4.5f).orElse(e.x1() + 40);
        float fineInps = inail.map(x -> x.x0() - 4.5f).orElse(fineCf + 80);
        float fineInail = unita.map(x -> x.x0() - 4.5f).orElse(fineInps + 75);
        cerca(p.testo(new Area(e.x0() - 8, fineCf, e.y() + 0.5f, e.y() + 22)), CODICE_FISCALE_AZIENDA)
                .ifPresent(v -> c.codiceFiscaleAzienda = v);
        cerca(p.testo(new Area(fineCf, fineInps, e.y() + 0.5f, e.y() + 22)), POSIZIONE)
                .ifPresent(v -> c.posizioneInps = v);
        cerca(p.testo(new Area(fineInps, fineInail, e.y() + 0.5f, e.y() + 22)), POSIZIONE)
                .ifPresent(v -> c.posizioneInail = v);
    }

    private static Optional<String> cerca(String testo, Pattern pattern) {
        var m = pattern.matcher(testo);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    private static void periodo(Pagina p, CedolinoLetto c) {
        var etichetta = p.prima(PERIODO);
        if (etichetta.isEmpty()) {
            c.etichettaMancante("PERIODO DI RETRIBUZIONE");
            return;
        }
        var e = etichetta.get();
        p.parole(new Area(e.x0() - 30, MAX, e.y() - 16, e.y() - 0.5f)).stream()
                .map(w -> Valori.leggiData(w.testo()))
                .flatMap(Optional::stream)
                .reduce((a, b) -> b)
                .ifPresent(d -> c.dataElaborazione = d);
        Periodo.leggi(Zone.testo(Zone.primaRigaSotto(p, e, 16, e.x0() - 20, MAX)), c);
        p.prima(VARIABILI).flatMap(v -> Zone.aDestra(p, v, 80, t -> Valori.leggiMeseAnno(t).isPresent()))
                .flatMap(w -> Valori.leggiMeseAnno(w.testo()))
                .ifPresent(m -> c.meseVariabili = m);
    }

    private static void dipendente(Pagina p, CedolinoLetto c) {
        var etichetta = p.prima(CODICE_DIPENDENTE);
        if (etichetta.isEmpty()) {
            c.etichettaMancante("CODICE DIPENDENTE");
        } else {
            var k = etichetta.get();
            var cognomeNome = Zone.sullaRiga(p, COGNOME_NOME, k.y(), Zone.STESSA_LINEA, k.x1());
            var codiceFiscale = Zone.sullaRiga(p, CODICE_FISCALE, k.y(), Zone.STESSA_LINEA, k.x1());
            var matricola = Zone.sullaRiga(p, MATRICOLA, k.y(), Zone.STESSA_LINEA, k.x1());
            Zone.rigaSotto(p, k, 22, k.x0() - 10, MAX).ifPresent(y -> {
                float fineCodice = cognomeNome.map(x -> x.x0() - 4.5f).orElse(k.x1() + 5);
                float fineNome = codiceFiscale.map(x -> x.x0() - 15).orElse(fineCodice + 250);
                float fineCf = matricola.map(x -> x.x0() - 4.5f).orElse(MAX);
                var codice = Zone.testoRiga(p, y, k.x0() - 10, fineCodice);
                var cf = Zone.testoRiga(p, y, fineNome, fineCf);
                if (cf != null) {
                    cerca(cf, Valori.CODICE_FISCALE).ifPresent(v -> c.codiceFiscale = v);
                }
                var mat = matricola.isPresent() ? Zone.testoRiga(p, y, fineCf, MAX) : null;
                c.codiceDipendente = codice != null ? codice : mat;
                var divisi = Classificazione.cognomeNome(Zone.testoRiga(p, y, fineCodice, fineNome), c.codiceFiscale);
                c.cognome = divisi[0];
                c.nome = divisi[1];
            });
        }
        var nascita = p.prima(DATA_NASCITA);
        if (nascita.isEmpty()) {
            c.etichettaMancante("DATA DI NASCITA");
            return;
        }
        var n = nascita.get();
        var etichette = new LinkedHashMap<String, Etichetta>();
        etichette.put("nascita", n);
        Zone.sullaRiga(p, DATA_ASSUNZIONE, n.y(), Zone.STESSA_LINEA, n.x1()).ifPresent(e -> etichette.put("assunzione", e));
        Zone.sullaRiga(p, DATA_CESSAZIONE, n.y(), Zone.STESSA_LINEA, n.x1()).ifPresent(e -> etichette.put("cessazione", e));
        float destra = etichette.values().stream().map(Etichetta::x1).max(Float::compare).orElseThrow() + 40;
        for (var w : Zone.sotto(p, n, 22, n.x0() - 15, destra)) {
            for (var data : date(w)) {
                String scelta = null;
                float distanza = 30;
                for (var e : etichette.entrySet()) {
                    float d = Math.abs(e.getValue().x0() - data.x());
                    if (d < distanza) {
                        distanza = d;
                        scelta = e.getKey();
                    }
                }
                if ("nascita".equals(scelta)) {
                    c.dataNascita = data.data();
                } else if ("assunzione".equals(scelta)) {
                    c.dataAssunzione = data.data();
                } else if ("cessazione".equals(scelta)) {
                    c.dataCessazione = data.data();
                }
            }
        }
        var presenze = p.prima(ORE_ORDINARIE).map(Etichetta::y).orElse(n.y() + 30);
        for (var r : p.righe(n.y() + 0.5f, presenze - 1)) {
            var m = RIGA_LIVELLO.matcher(r.testo());
            if (m.matches()) {
                c.qualifica = m.group(1).isBlank() ? null : m.group(1).strip();
                c.livello = m.group(2).strip();
                break;
            }
        }
        presenze(p, c);
    }

    private record DataStampata(LocalDate data, float x) {
    }

    /** Date di una parola: "15-01-1985", "15/01/1985" o blocchi attaccati di 8 cifre ("1501198516032020"). */
    private static List<DataStampata> date(Parola w) {
        var testo = w.testo();
        var data = Valori.leggiData(testo);
        if (data.isPresent()) {
            return List.of(new DataStampata(data.get(), w.x0()));
        }
        if (!testo.matches("(\\d{8})+")) {
            return List.of();
        }
        var date = new ArrayList<DataStampata>();
        float larghezzaCifra = (w.x1() - w.x0()) / testo.length();
        for (int i = 0; i < testo.length(); i += 8) {
            var blocco = testo.substring(i, i + 8);
            float x = w.x0() + i * larghezzaCifra;
            Valori.leggiDataCompatta(blocco).ifPresent(d -> date.add(new DataStampata(d, x)));
        }
        return date;
    }

    private static void presenze(Pagina p, CedolinoLetto c) {
        p.prima(ORE_ORDINARIE).ifPresentOrElse(o -> {
            var intestazioni = Zone.stessaRiga(p, o, -MAX, MAX);
            var valori = riga(Zone.sotto(p, o, 14, -MAX, MAX)).stream()
                    .filter(w -> Zone.NUMERO.test(w.testo()))
                    .toList();
            var colonne = new Colonne<Integer>();
            for (int i = 0; i < intestazioni.size(); i++) {
                colonne.colonna(i, intestazioni.get(i).centro());
            }
            var testi = intestazioni.stream().map(w -> w.testo().toUpperCase(Locale.ROOT)).toList();
            int primoGiorni = testi.indexOf("GIORNI");
            colonne.assegna(valori, 20).forEach((i, w) -> {
                var valore = Valori.leggiNumero(w.testo()).orElse(null);
                var t = testi.get(i);
                boolean seguonoOreOrdinarie = i + 2 < testi.size() && testi.get(i + 1).equals("ORE")
                        && testi.get(i + 2).equals("ORDINARIE");
                if (t.equals("GIORNI") && i == primoGiorni) {
                    c.giorniRetribuiti = valore;
                } else if (t.equals("GIORNI") && seguonoOreOrdinarie) {
                    c.giorniLavorati = valore;
                } else if (t.equals("ORE") && i + 1 < testi.size() && testi.get(i + 1).equals("ORDINARIE")
                        || t.equals("ORDINARIE")) {
                    c.oreLavorate = valore;
                }
            });
        }, () -> c.etichettaMancante("ORE ORDINARIE"));
    }

    private static List<Parola> riga(List<Parola> parole) {
        if (parole.isEmpty()) {
            return parole;
        }
        float y = parole.getFirst().y();
        return parole.stream().filter(w -> Math.abs(w.y() - y) <= DocumentoTesto.TOLLERANZA_RIGA).toList();
    }

    private static void elementi(Pagina p, CedolinoLetto c, float yMax) {
        var minimo = Zone.prima(p, MINIMO, 0, yMax);
        minimo.ifPresentOrElse(m -> {
            var colonne = new LinkedHashMap<Zone.Gruppo, Etichetta>();
            Zone.gruppi(p, m.y(), m.x0() - 1, p.larghezza() * 0.8f, 6).forEach(g -> colonne.put(g, g.etichetta()));
            Zone.valoriSotto(p, colonne, 16, p.larghezza() * 0.8f, Zone.IMPORTO)
                    .forEach((g, w) -> Periodo.elemento(c, g.compatto(), Valori.leggiImporto(w.testo()).orElseThrow()));
        }, () -> c.etichettaMancante("MINIMO"));
        float da = minimo.map(Etichetta::y).orElse(yMax - 80);
        p.cerca(TOTALE).stream()
                .filter(t -> t.y() > da && t.y() < yMax && t.x0() > p.larghezza() * 0.6f)
                .findFirst()
                .flatMap(t -> Zone.primaSotto(p, t, 20, t.x0() - 40, MAX, Zone.IMPORTO))
                .ifPresent(w -> c.totaleRetribuzione = Valori.leggiImporto(w.testo()).orElseThrow());
        p.cerca(PROSSIMO_SCATTO).stream()
                .filter(t -> t.y() > da && t.y() < yMax)
                .findFirst()
                .flatMap(t -> Zone.primaSotto(p, t, 22, t.x0() - 25, t.x1() + 25,
                        x -> Valori.leggiMeseAnno(x).isPresent() || Valori.leggiData(x).isPresent()))
                .ifPresent(w -> c.dataProssimoScatto = Valori.leggiData(w.testo())
                        .orElseGet(() -> Valori.leggiMeseAnno(w.testo()).orElseThrow().atDay(1)));
    }

    // ---------------------------------------------------------------------------------------------------- voci

    private static float voci(Pagina p, CedolinoLetto c) {
        var riferimento = p.prima(RIFERIMENTO);
        if (riferimento.isEmpty()) {
            c.etichettaMancante("intestazione delle voci");
            return Float.NaN;
        }
        var r = riferimento.get();
        float y = r.y();
        var base = Zone.sullaRiga(p, IMPORTO_BASE, y, Zone.STESSA_LINEA, -MAX);
        var trattenute = Zone.sullaRiga(p, TRATTENUTE, y, Zone.STESSA_LINEA, r.x1());
        var competenze = Zone.sullaRiga(p, COMPETENZE, y, Zone.STESSA_LINEA, r.x1());
        if (base.isEmpty() || trattenute.isEmpty() || competenze.isEmpty()) {
            c.etichettaMancante("colonne delle voci");
            return y;
        }
        var colonne = new Colonne<String>()
                .colonna("base", base.get())
                .colonna("riferimento", r)
                .colonna("trattenuta", trattenute.get())
                .colonna("competenza", competenze.get());
        float fine = p.altezza();
        for (var marcatore : List.of(CONGUAGLIO, PROGRESSIVI, TOTALE_COMPETENZE)) {
            var trovato = p.cerca(marcatore).stream().filter(e -> e.y() > y + 1).findFirst();
            if (trovato.isPresent()) {
                fine = Math.min(fine, trovato.get().y());
            }
        }
        float inizioBase = base.get().x0();
        for (var riga : p.righe(y + 3, fine - 2)) {
            var parole = riga.parole().stream().filter(w -> !w.testo().chars().allMatch(ch -> ch == '*')).toList();
            if (parole.isEmpty()) {
                continue;
            }
            String codice = null;
            int i = 0;
            var primo = parole.getFirst();
            if (CODICE_VOCE.matcher(primo.testo()).matches() && primo.x1() < inizioBase - 20) {
                codice = primo.testo().replace("*", "");
                i = 1;
            }
            var testo = new ArrayList<String>();
            String unita = null;
            BigDecimal quantita = null;
            BigDecimal valoreBase = null;
            BigDecimal competenza = null;
            BigDecimal trattenuta = null;
            for (; i < parole.size(); i++) {
                var w = parole.get(i);
                var t = w.testo();
                boolean percentuale = t.endsWith("%") && Valori.importo(t.substring(0, t.length() - 1));
                if (Valori.importo(t) || percentuale) {
                    var v = Valori.leggiImporto(percentuale ? t.substring(0, t.length() - 1) : t).orElseThrow();
                    var colonna = colonne.vicina(w.centro(), 60);
                    if (colonna == null) {
                        testo.add(t);
                        continue;
                    }
                    switch (colonna) {
                        case "base" -> valoreBase = v;
                        case "riferimento" -> {
                            quantita = v;
                            if (percentuale) {
                                unita = "%";
                            }
                        }
                        case "trattenuta" -> trattenuta = v;
                        default -> competenza = v;
                    }
                } else if (w.x0() > r.x0() - 10 && Classificazione.unitaVoce(t) != null
                        && t.matches("(?i)GG\\.?|ORE|HH|%|KM|GIORNI")) {
                    unita = t;
                } else {
                    testo.add(t);
                }
            }
            if (codice == null && quantita == null && valoreBase == null && competenza == null && trattenuta == null) {
                continue;
            }
            var descrizione = String.join(" ", testo);
            c.voce(codice, descrizione, quantita, Classificazione.unitaVoce(unita), valoreBase, competenza, trattenuta,
                    null);
            var d = Classificazione.maiuscolo(descrizione);
            if (competenza == null && trattenuta == null && RETRIBUZIONE_UTILE_TFR.matcher(d).find()) {
                c.tfrRetribuzioneUtile = valoreBase;
            } else if (competenza == null && trattenuta == null && QUOTA_TFR.matcher(d).find()) {
                c.tfrQuotaMese = valoreBase;
            } else if (Classificazione.buoniPasto(d) && c.buoniPastoImporto == null) {
                c.buoniPastoNumero = quantita;
                c.buoniPastoValore = valoreBase;
                c.buoniPastoImporto = competenza != null ? competenza : trattenuta;
            }
        }
        return y;
    }

    // ------------------------------------------------------------------------ progressivi, TFR, ratei, totali

    private static void progressivi(Pagina p, CedolinoLetto c) {
        var progressivi = p.prima(PROGRESSIVI);
        if (progressivi.isEmpty()) {
            c.etichettaMancante("PROGRESSIVI");
            return;
        }
        var g = progressivi.get();
        var colonne = new LinkedHashMap<String, Etichetta>();
        Zone.prima(p, IMP_INPS, g.y() - 2, g.y() + 12).ifPresent(e -> colonne.put("inps", e));
        Zone.prima(p, IMP_INAIL, g.y() - 2, g.y() + 12).ifPresent(e -> colonne.put("inail", e));
        Zone.prima(p, IMP_IRPEF, g.y() - 2, g.y() + 12).ifPresent(e -> colonne.put("irpef", e));
        Zone.prima(p, IRPEF_PAGATA, g.y() - 2, g.y() + 12).ifPresent(e -> colonne.put("pagata", e));
        var valori = Zone.valoriSotto(p, colonne, 16, p.larghezza(), Zone.IMPORTO);
        c.imponibilePrevidenziale = Zone.importo(valori, "inps");
        c.imponibileIrpef = Zone.importo(valori, "irpef");
        c.irpefTrattenuta = Zone.importo(valori, "pagata");
        Zone.prima(p, FONDO_TFR, g.y(), g.y() + 50).ifPresent(f -> {
            var pattern = new LinkedHashMap<String, Pattern>();
            pattern.put("fondo", FONDO_TFR);
            pattern.put("rivalutazione", RIVALUTAZIONE);
            pattern.put("imposta", IMPOSTA_RIVALUTAZIONE);
            pattern.put("quotaAnno", QUOTA_ANNO);
            pattern.put("fondi", TFR_FONDI);
            pattern.put("quir", TFR_QUIR);
            pattern.put("anticipi", ANTICIPI);
            var tfr = Zone.valoriSotto(p, Zone.etichette(p, f.y(), Zone.STESSA_LINEA, pattern), 14, p.larghezza(), Zone.IMPORTO);
            c.tfrFondo = Zone.importo(tfr, "fondo");
            c.tfrRivalutazione = Zone.importo(tfr, "rivalutazione");
            c.tfrImpostaSostitutiva = Zone.importo(tfr, "imposta");
            c.tfrQuotaAnno = Zone.importo(tfr, "quotaAnno");
            c.tfrQuotaFondi = Zone.importo(tfr, "fondi");
            c.tfrQuotaBusta = Zone.importo(tfr, "quir");
            c.tfrAnticipi = Zone.importo(tfr, "anticipi");
        });
    }

    private static void ratei(Pagina p, CedolinoLetto c) {
        var ratei = p.cerca(RATEI).stream().filter(e -> e.x0() < p.larghezza() * 0.3f).reduce((a, b) -> b);
        if (ratei.isEmpty()) {
            c.etichettaMancante("RATEI");
            return;
        }
        var e = ratei.get();
        var residuo = Zone.prima(p, RESIDUO, e.y() - 2, e.y() + 12);
        if (residuo.isEmpty()) {
            c.etichettaMancante("colonne dei ratei");
            return;
        }
        var res = residuo.get();
        var colonne = new LinkedHashMap<String, Etichetta>();
        colonne.put("residuo", res);
        Zone.sullaRiga(p, MATURATO, res.y(), Zone.STESSA_LINEA, res.x1()).ifPresent(x -> colonne.put("maturato", x));
        Zone.sullaRiga(p, GODUTO, res.y(), Zone.STESSA_LINEA, res.x1()).ifPresent(x -> colonne.put("goduto", x));
        var saldo = Zone.sullaRiga(p, SALDO, res.y(), Zone.STESSA_LINEA, res.x1());
        saldo.ifPresent(x -> colonne.put("saldo", x));
        float limite = p.cerca(TOTALE_COMPETENZE).stream().map(Etichetta::x0).findFirst()
                .orElse(p.larghezza() * 0.7f) - 5;
        float inizioUnita = saldo.map(s -> s.x1() + 10).orElse(limite - 60);
        for (var riga : p.righe(res.y() + 3, res.y() + 40)) {
            var etichetta = Zone.testo(riga.parole().stream()
                    .filter(w -> w.x1() < res.x0() - 2 && !Valori.importo(w.testo())).toList());
            var tipo = Classificazione.tipoRateo(etichetta);
            if (tipo.isEmpty()) {
                continue;
            }
            // valori ed etichetta possono avere linee di base distanti fino a un paio di punti
            var parole = p.parole(new Area(res.x0() - 30, limite, riga.y() - Zone.STESSA_LINEA,
                    riga.y() + Zone.STESSA_LINEA));
            var valori = parole.stream().filter(w -> Valori.importo(w.testo())).toList();
            if (valori.isEmpty()) {
                continue;
            }
            var assegnati = Zone.perInizio(colonne, valori, 6);
            var unita = parole.stream().filter(w -> w.x0() >= inizioUnita && !Valori.importo(w.testo()))
                    .map(w -> Classificazione.unitaRateo(w.testo()))
                    .flatMap(Optional::stream)
                    .findFirst();
            c.ratei.add(new Rateo(tipo.get(), unita.orElse(LayoutJobSistemi.unitaPredefinita(tipo.get())),
                    Zone.oZero(Zone.importo(assegnati, "residuo")),
                    Zone.oZero(Zone.importo(assegnati, "maturato")),
                    Zone.oZero(Zone.importo(assegnati, "goduto")),
                    Zone.oZero(Zone.importo(assegnati, "saldo"))));
        }
    }

    private static void totali(Pagina p, CedolinoLetto c) {
        c.totaleCompetenze = aDestra(p, TOTALE_COMPETENZE);
        c.lordo = c.totaleCompetenze;
        c.totaleTrattenute = aDestra(p, TOTALE_TRATTENUTE);
        c.arrotondamento = aDestra(p, ARROTONDAMENTO);
        if (c.totaleCompetenze == null) {
            c.etichettaMancante("TOTALE COMPETENZE");
        }
        p.prima(NETTO).ifPresentOrElse(n -> p.parole(new Area(n.x0() - 40, MAX, n.y() - 2, n.y() + 22)).stream()
                        .filter(w -> Valori.importo(w.testo()))
                        .findFirst()
                        .ifPresent(w -> c.netto = Valori.leggiImporto(w.testo()).orElseThrow()),
                () -> c.etichettaMancante("NETTO DEL MESE"));
    }

    /** Importo stampato a destra di un'etichetta dei totali (stessa riga o poco sotto). */
    private static BigDecimal aDestra(Pagina p, Pattern etichetta) {
        return p.prima(etichetta)
                .flatMap(e -> p.parole(new Area(e.x1() + 2, MAX, e.y() - 2, e.y() + 8)).stream()
                        .filter(w -> Valori.importo(w.testo()) && w.x0() > e.x1() + 2)
                        .findFirst())
                .flatMap(w -> Valori.leggiImporto(w.testo()))
                .orElse(null);
    }
}
