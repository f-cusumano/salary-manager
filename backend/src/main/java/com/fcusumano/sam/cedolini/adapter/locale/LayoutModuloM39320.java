package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.adapter.locale.CedolinoLetto.Rateo;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Pagina;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Parola;
import com.fcusumano.sam.cedolini.adapter.locale.DocumentoTesto.Riga;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cedolino su modulo prestampato "(M39320)" usato da studi paghe ("Elaborato da: ..."): le etichette sono stampate
 * sul modulo e non nel testo del PDF, quindi i campi si leggono per posizione (coordinate del foglio A4, scalate se la
 * pagina ha un formato diverso). Un cedolino puo' occupare piu' fogli ("Continua su foglio numero" / "Segue da
 * foglio numero"): voci di tutti i fogli, totali, netto, ratei e TFR dall'ultimo.
 */
final class LayoutModuloM39320 implements LayoutCedolino {

    static final String ID = "modulo-m39320";

    private static final float LARGHEZZA_A4 = 595.32f;
    private static final float ALTEZZA_A4 = 841.92f;
    private static final float TOLLERANZA = 4;

    // righe dell'intestazione (linea di base sul foglio A4)
    private static final float RIGA_AZIENDA = 44;
    private static final float RIGA_DIPENDENTE = 68.2f;
    private static final float RIGA_QUALIFICA = 116.2f;
    private static final float RIGA_DATE = 140.2f;
    private static final float RIGA_PERIODO = 164.3f;
    private static final float RIGA_TOTALI = 561.1f;
    private static final float FINE_VOCI = 553;

    private static final Pattern RETRIBUZIONE_BASE = Zone.etichetta("RETRIBUZIONE BASE :");
    private static final Pattern PAGA_ORARIA = Zone.etichetta("Paga oraria :");
    private static final Pattern PAGA_GIORNALIERA = Zone.etichetta("Paga giorn. :");
    private static final Pattern TOTALE = Zone.etichetta("Totale :");
    private static final Pattern SEGUE = Zone.etichetta("Segue da foglio");
    private static final Pattern PROSSIMO_SCATTO = Zone.etichetta("Prossimo scatto");
    private static final Pattern INTESTAZIONE_RATEI = Zone.etichetta("Gg.");
    private static final Pattern TFR_AL = Pattern.compile("TFRAL31/12/\\d{2}");
    private static final Pattern CALENDARIO_PRECEDENTE = Pattern.compile("CALENDARIODELLEPRESENZE.{0,12}MESEPRECEDENTE");
    private static final Pattern FONDO_TFR = Pattern.compile("^T\\.?F\\.?R\\.?\\s*31/12\\s*A\\.?P");
    private static final Pattern RIVALUTAZIONE_TFR = Pattern.compile("^RIV\\.?\\s*TFR");
    private static final Pattern IMPOSTA_TFR = Pattern.compile("^IMP\\.?\\s*SOST.*TFR");
    private static final Pattern QUOTA_ANNO_TFR = Pattern.compile("^T\\.?F\\.?R\\.?\\s*ANNO");
    private static final Pattern INDENNITA_ANZIANITA = Pattern.compile("IND\\.?\\s*ANZ");
    private static final Pattern RITENUTA_TFR = Pattern.compile("^TFR\\s*IMPON");
    private static final Pattern POSIZIONE_INAIL = Pattern.compile("\\d{5,10}/\\d{1,3}");

    /** Colonne delle voci sul foglio A4: estremi sinistri. */
    private static final float[] COLONNE_VOCI = {30, 62, 213, 286, 344, 418, 498, 582};
    private static final String[] NOMI_COLONNE = {"codice", "descrizione", "quantita", "percentuale", "base",
            "trattenuta", "competenza"};

    /** Gruppi dei ratei sul foglio A4: tipo, unita' e estremi delle colonne spettanti / godute / residue. */
    private record GruppoRatei(TipoRateo tipo, UnitaMisura unita, float[] estremi) {
    }

    private static final List<GruppoRatei> RATEI = List.of(
            new GruppoRatei(TipoRateo.FERIE, UnitaMisura.GIORNI, new float[] {33, 102, 157, 223}),
            new GruppoRatei(TipoRateo.EX_FESTIVITA, UnitaMisura.ORE, new float[] {223, 278, 331, 403}),
            new GruppoRatei(TipoRateo.PERMESSI, UnitaMisura.ORE, new float[] {403, 456, 516, 582}));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String nome() {
        return "Modulo continuo M39320 (studi paghe)";
    }

    @Override
    public boolean riconosce(DocumentoTesto documento) {
        var testo = documento.compatto();
        return testo.contains("(M39320)") && (testo.contains("PAGAORARIA") || testo.contains("RETRIBUZIONEBASE"));
    }

    @Override
    public CedolinoLetto leggi(DocumentoTesto documento) {
        var c = new CedolinoLetto();
        var pagine = documento.pagine();
        intestazione(pagine.getFirst(), c);
        var chiavi = new java.util.LinkedHashSet<String>();
        var fogli = new ArrayList<Pagina>();
        for (var p : pagine) {
            var chiave = campo(p, RIGA_DATE, 261, 372) + "|" + campo(p, RIGA_PERIODO, 102.5f, 223);
            chiavi.add(chiave);
            if (chiavi.size() == 1) {
                fogli.add(p);
            }
        }
        c.numeroCedolini = chiavi.size();
        for (var p : fogli) {
            voci(p, c, p == fogli.getFirst());
        }
        var ultimo = fogli.getLast();
        totali(ultimo, c);
        ratei(ultimo, c);
        tfr(ultimo, c);
        if (CALENDARIO_PRECEDENTE.matcher(documento.compatto().replace("\n", "")).find() && c.periodo != null) {
            c.meseVariabili = c.periodo.minusMonths(1);
        }
        c.controllaSomme();
        return c;
    }

    // ----------------------------------------------------------------------------------------------- posizioni

    private static float sx(Pagina p, float x) {
        return x * p.larghezza() / LARGHEZZA_A4;
    }

    private static float sy(Pagina p, float y) {
        return y * p.altezza() / ALTEZZA_A4;
    }

    private static Optional<Riga> riga(Pagina p, float y) {
        float atteso = sy(p, y);
        return p.righe().stream().filter(r -> Math.abs(r.y() - atteso) <= TOLLERANZA).findFirst();
    }

    private static List<Parola> parole(Riga r, Pagina p, float x0, float x1) {
        return r.parole().stream().filter(w -> w.centro() >= sx(p, x0) && w.centro() < sx(p, x1)).toList();
    }

    /** Testo stampato in una riga tra due ascisse del foglio A4; null se vuoto. */
    private static String campo(Pagina p, float y, float x0, float x1) {
        return riga(p, y).map(r -> Zone.testo(parole(r, p, x0, x1))).filter(t -> !t.isEmpty()).orElse(null);
    }

    private static BigDecimal numero(Pagina p, Riga r, float x0, float x1) {
        return parole(r, p, x0, x1).stream()
                .map(w -> Valori.leggiNumero(w.testo()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private static String senzaTrattino(String testo) {
        return testo == null ? null : testo.replaceAll("\\s*-\\s*$", "").strip();
    }

    // ------------------------------------------------------------------------------------------- intestazione

    private static void intestazione(Pagina p, CedolinoLetto c) {
        c.codiceAzienda = senzaTrattino(campo(p, RIGA_AZIENDA, 30, 125));
        c.ragioneSociale = campo(p, RIGA_AZIENDA, 125, 425);
        var inps = campo(p, RIGA_AZIENDA, 425, 590);
        c.posizioneInps = inps == null ? null : inps.replace(" ", "");
        var matricola = campo(p, RIGA_DIPENDENTE, 30, 125);
        if (matricola != null) {
            var parti = matricola.split("\\s+");
            var ultima = parti[parti.length - 1];
            c.codiceDipendente = ultima.matches("[A-Za-z0-9]+") ? ultima : null;
        }
        c.cognome = campo(p, RIGA_DIPENDENTE, 125, 330);
        c.nome = campo(p, RIGA_DIPENDENTE, 330, 590);
        var qualifica = senzaTrattino(campo(p, RIGA_QUALIFICA, 33, 163));
        var mansione = campo(p, RIGA_QUALIFICA, 163, 388);
        if (mansione != null && mansione.contains("-")) {
            var livello = mansione.substring(mansione.lastIndexOf('-') + 1).strip();
            if (!livello.isEmpty() && livello.length() <= 4) {
                c.livello = livello;
                mansione = mansione.substring(0, mansione.lastIndexOf('-')).strip();
            }
        }
        c.qualifica = qualifica == null ? mansione
                : mansione == null || mansione.isEmpty() ? qualifica : qualifica + " - " + mansione;
        var inail = campo(p, RIGA_QUALIFICA, 388, 489);
        if (inail != null) {
            var m = POSIZIONE_INAIL.matcher(inail);
            if (m.find()) {
                c.posizioneInail = m.group();
            }
        }
        c.dataNascita = Valori.leggiData(campo(p, RIGA_DATE, 33, 97)).orElse(null);
        c.dataAssunzione = Valori.leggiData(campo(p, RIGA_DATE, 97, 163)).orElse(null);
        c.dataCessazione = Valori.leggiData(campo(p, RIGA_DATE, 163, 227)).orElse(null);
        var cf = campo(p, RIGA_DATE, 261, 372);
        c.codiceFiscale = Valori.codiceFiscale(cf) ? cf : null;
        c.dataElaborazione = Valori.leggiData(campo(p, RIGA_PERIODO, 33, 102.5f)).orElse(null);
        Periodo.leggi(campo(p, RIGA_PERIODO, 102.5f, 223), c);
        riga(p, RIGA_PERIODO).ifPresent(r -> {
            c.oreLavorate = numero(p, r, 223, 284);
            c.giorniLavorati = numero(p, r, 284, 344);
            c.giorniRetribuiti = numero(p, r, 344, 395);
        });
        if (c.codiceFiscale == null || c.periodo == null) {
            c.etichettaMancante("intestazione del modulo");
        }
        c.retribuzioneBase = valoreDopo(p, RETRIBUZIONE_BASE);
        c.pagaOraria = valoreDopo(p, PAGA_ORARIA);
        c.pagaGiornaliera = valoreDopo(p, PAGA_GIORNALIERA);
        c.totaleRetribuzione = valoreDopo(p, TOTALE);
    }

    private static BigDecimal valoreDopo(Pagina p, Pattern etichetta) {
        return p.prima(etichetta)
                .flatMap(e -> Zone.aDestra(p, e, 140, Zone.IMPORTO))
                .flatMap(w -> Valori.leggiImporto(w.testo()))
                .orElse(null);
    }

    // ---------------------------------------------------------------------------------------------------- voci

    private static void voci(Pagina p, CedolinoLetto c, boolean primo) {
        var inizio = (primo ? p.prima(PAGA_ORARIA) : p.prima(SEGUE)).map(e -> e.y() + 3);
        if (inizio.isEmpty()) {
            c.etichettaMancante(primo ? "Paga oraria" : "Segue da foglio");
            return;
        }
        for (var r : p.righe(inizio.get(), sy(p, FINE_VOCI))) {
            var valori = new LinkedHashMap<String, BigDecimal>();
            var testo = new ArrayList<String>();
            String codice = null;
            boolean traParentesi = false;
            for (var w : r.parole()) {
                var t = w.testo();
                if (t.matches("_+")) {
                    continue;
                }
                var colonna = colonna(p, w.centro());
                boolean importi = colonna != null && !"descrizione".equals(colonna) && !"codice".equals(colonna);
                if (importi && (t.equals("(") || t.equals(")"))) {
                    // importo informativo stampato tra parentesi: va nella base
                    traParentesi = t.equals("(");
                    continue;
                }
                var numero = t.replaceAll("^\\(|\\)$", "");
                if ("codice".equals(colonna) && t.matches("\\d{1,4}")) {
                    codice = t;
                } else if (importi && Valori.importo(numero)) {
                    valori.put(traParentesi || t.startsWith("(") || t.endsWith(")") ? "base" : colonna,
                            Valori.leggiImporto(numero).orElseThrow());
                    traParentesi = false;
                } else {
                    testo.add(t);
                }
            }
            if (codice == null && valori.isEmpty()) {
                continue;
            }
            var descrizione = String.join(" ", testo);
            var quantita = valori.get("quantita");
            String unita = null;
            if (quantita == null && valori.containsKey("percentuale")) {
                quantita = valori.get("percentuale");
                unita = "%";
            }
            var base = valori.get("base");
            var competenza = valori.get("competenza");
            var trattenuta = valori.get("trattenuta");
            c.voce(codice, descrizione, quantita, unita, base, competenza, trattenuta, null);
            vociSpeciali(c, Classificazione.maiuscolo(descrizione), quantita, base, competenza, trattenuta);
        }
    }

    private static String colonna(Pagina p, float x) {
        for (int i = 0; i < NOMI_COLONNE.length; i++) {
            if (x >= sx(p, COLONNE_VOCI[i]) && x < sx(p, COLONNE_VOCI[i + 1])) {
                return NOMI_COLONNE[i];
            }
        }
        return null;
    }

    private static void vociSpeciali(CedolinoLetto c, String d, BigDecimal quantita, BigDecimal base,
                                     BigDecimal competenza, BigDecimal trattenuta) {
        if (FONDO_TFR.matcher(d).find() && base != null) {
            c.tfrFondo = base;
        } else if (RIVALUTAZIONE_TFR.matcher(d).find() && base != null) {
            c.tfrRivalutazione = base;
        } else if (IMPOSTA_TFR.matcher(d).find() && base != null) {
            c.tfrImpostaSostitutiva = base;
        } else if (QUOTA_ANNO_TFR.matcher(d).find() && base != null) {
            c.tfrQuotaAnno = base;
        } else if (INDENNITA_ANZIANITA.matcher(d).find() && competenza != null) {
            c.tfrLiquidatoLordo = competenza;
        } else if (RITENUTA_TFR.matcher(d).find() && trattenuta != null) {
            c.tfrRitenuta = trattenuta;
        } else if (Classificazione.buoniPasto(d) && c.buoniPastoImporto == null) {
            c.buoniPastoNumero = quantita;
            c.buoniPastoValore = base;
            c.buoniPastoImporto = competenza != null ? competenza : trattenuta;
        }
    }

    // ------------------------------------------------------------------------------------------ ultimo foglio

    private static void totali(Pagina p, CedolinoLetto c) {
        riga(p, RIGA_TOTALI).ifPresentOrElse(r -> {
            for (var w : r.parole()) {
                var v = Valori.leggiImporto(w.testo());
                if (v.isEmpty()) {
                    continue;
                }
                if (w.centro() < sx(p, 498)) {
                    c.totaleTrattenute = v.get();
                } else {
                    c.totaleCompetenze = v.get();
                }
            }
        }, () -> c.etichettaMancante("totali"));
        c.lordo = c.totaleCompetenze;
        var prossimo = p.prima(PROSSIMO_SCATTO);
        if (prossimo.isEmpty()) {
            c.etichettaMancante("Prossimo scatto / netto");
            return;
        }
        var e = prossimo.get();
        Zone.stessaRiga(p, e, sx(p, 450), Float.MAX_VALUE).stream()
                .filter(w -> Valori.importo(w.testo()))
                .reduce((a, b) -> b)
                .ifPresent(w -> c.netto = Valori.leggiImporto(w.testo()).orElseThrow());
        var successiva = p.righe(e.y() + 3, e.y() + 20).stream().findFirst();
        successiva.ifPresent(r -> {
            parole(r, p, 100, 170).stream()
                    .map(w -> Valori.leggiData(w.testo()))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .ifPresent(d -> c.dataProssimoScatto = d);
            var precedente = numero(p, r, 280, 360);
            var attuale = numero(p, r, 360, 440);
            if (precedente != null || attuale != null) {
                c.arrotondamento = Zone.oZero(attuale).subtract(Zone.oZero(precedente));
            }
        });
    }

    private static void ratei(Pagina p, CedolinoLetto c) {
        var intestazione = p.cerca(INTESTAZIONE_RATEI).stream().filter(e -> e.y() > sy(p, RIGA_TOTALI)).findFirst();
        if (intestazione.isEmpty()) {
            c.etichettaMancante("ratei");
            return;
        }
        var righe = p.righe(intestazione.get().y() + 3, intestazione.get().y() + 30);
        if (righe.size() < 2) {
            return;
        }
        var annoPrecedente = righe.get(0);
        var annoCorrente = righe.get(1);
        for (var g : RATEI) {
            var e = g.estremi();
            var spettantiAp = numero(p, annoPrecedente, e[0], e[1]);
            var goduteAp = numero(p, annoPrecedente, e[1], e[2]);
            var residueAp = numero(p, annoPrecedente, e[2], e[3]);
            var spettantiAc = numero(p, annoCorrente, e[0], e[1]);
            var goduteAc = numero(p, annoCorrente, e[1], e[2]);
            var residueAc = numero(p, annoCorrente, e[2], e[3]);
            if (spettantiAp == null && spettantiAc == null && residueAp == null && residueAc == null) {
                continue;
            }
            c.ratei.add(new Rateo(g.tipo(), g.unita(), Zone.oZero(spettantiAp), Zone.oZero(spettantiAc),
                    Zone.oZero(goduteAp).add(Zone.oZero(goduteAc)), Zone.oZero(residueAp).add(Zone.oZero(residueAc))));
        }
    }

    private static void tfr(Pagina p, CedolinoLetto c) {
        var etichetta = p.cerca(TFR_AL).stream().findFirst();
        if (etichetta.isEmpty()) {
            return;
        }
        p.righe(etichetta.get().y() + 3, etichetta.get().y() + 16).stream().findFirst().ifPresent(r -> {
            c.tfrRetribuzioneUtile = numero(p, r, 33, 128);
            c.imponibileIrpef = numero(p, r, 128, 189);
            c.irpefTrattenuta = numero(p, r, 189, 268);
            var fondo = numero(p, r, 343, 423);
            if (c.tfrFondo == null) {
                c.tfrFondo = fondo;
            }
        });
    }
}
