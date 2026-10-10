package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regole comuni a tutti i layout: categoria delle voci dalla descrizione e dalla colonna dell'importo, tipo e unita'
 * dei ratei, tipo del cedolino. Sono le stesse regole date a Claude nel prompt di estrazione, scritte come codice.
 */
final class Classificazione {

    private static final Pattern FISCALE = Pattern.compile(
            "IRPEF|ADDIZ|ADD\\.(REG|COM)|ACC\\.ADD|RIT(ENUT[AE])?\\.?\\s*(FIS|IRPEF)|RIT\\.FIS|RITENUTE IRPEF|CREDITO|"
                    + "TRAT\\.?\\s*INTEGR|TRATTAMENTO INTEGRATIVO|BONUS|MOD\\.?\\s*730|IMP\\.?\\s*SOST|IMPOSTA|"
                    + "TFR IMPON|CONG\\.?\\s*(FISC|IRPEF)|DETRAZ(?!\\.?\\s*(ORE|GG|GIORNI)\\b)|ULTER");
    private static final Pattern PREVIDENZIALE = Pattern.compile(
            "CONTRIB|INPS|\\bIVS\\b|\\bFIS\\b|FIS\\d|\\bFAP\\b|F\\.?P\\.?L\\.?D|PREVID|FONDO|\\bF\\.DO\\b|ASS\\.?\\s*SAN|"
                    + "CASSA|COMETA|FONTE|EST\\b|METASALUTE|SANITA");
    private static final Pattern NON_RETRIBUTIVA = Pattern.compile(
            "RIMBORS|CHILOMETRIC|\\bKM\\b|DIARIA|TRASFERT|BUON[IO]\\s*PASTO|TICKET|WELFARE|FRINGE");
    private static final Pattern TREDICESIMA = Pattern.compile(
            "13\\s*[.°ª^]?\\s*(MA|A)?\\s*MENSILIT|TREDICESIM|GRATIFICA NATALIZIA");
    private static final Pattern QUATTORDICESIMA = Pattern.compile(
            "14\\s*[.°ª^]?\\s*(MA|A)?\\s*MENSILIT|QUATTORDICESIM");
    private static final Pattern BUONI_PASTO = Pattern.compile("BUON[IO]\\s*PASTO|TICKET");

    private Classificazione() {
    }

    /**
     * Categoria di una voce.
     *
     * @param competenza  importo nella colonna competenze
     * @param trattenuta  importo nella colonna trattenute
     * @param nelNetto    false se il cedolino indica che la voce non entra nel netto (es. colonna "N" di JOB vuota),
     *                    null se il layout non lo indica
     * @param quantita    true se la voce riporta una quantita' (ore, giorni)
     */
    static CategoriaVoce categoria(String descrizione, BigDecimal competenza, BigDecimal trattenuta, Boolean nelNetto,
                                   boolean quantita) {
        var d = maiuscolo(descrizione);
        if (competenza == null && trattenuta == null) {
            return quantita ? CategoriaVoce.PRESENZA : CategoriaVoce.INFORMATIVA;
        }
        if (Boolean.FALSE.equals(nelNetto)) {
            return CategoriaVoce.INFORMATIVA;
        }
        if (FISCALE.matcher(d).find()) {
            return CategoriaVoce.TRATTENUTA_FISCALE;
        }
        // la colonna di stampa decide la natura della voce, anche con importo negativo (es. esonero contributivo)
        boolean inTrattenuta = trattenuta != null && (competenza == null || competenza.signum() == 0);
        if (inTrattenuta) {
            return PREVIDENZIALE.matcher(d).find() ? CategoriaVoce.TRATTENUTA_PREVIDENZIALE
                    : CategoriaVoce.ALTRA_TRATTENUTA;
        }
        return NON_RETRIBUTIVA.matcher(d).find() ? CategoriaVoce.NON_RETRIBUTIVA : CategoriaVoce.COMPETENZA;
    }

    static boolean tredicesima(String testo) {
        return testo != null && TREDICESIMA.matcher(maiuscolo(testo)).find();
    }

    static boolean quattordicesima(String testo) {
        return testo != null && QUATTORDICESIMA.matcher(maiuscolo(testo)).find();
    }

    static boolean buoniPasto(String testo) {
        return testo != null && BUONI_PASTO.matcher(maiuscolo(testo)).find();
    }

    /** Tipo di rateo dall'etichetta stampata ("FERIE", "PERMESSI (R.O.L.)", "P.I.R.", "EX FESTIVITA'", "Banca ore"). */
    static Optional<TipoRateo> tipoRateo(String etichetta) {
        var e = maiuscolo(etichetta).replace(" ", "");
        if (e.startsWith("FERIE")) {
            return Optional.of(TipoRateo.FERIE);
        }
        if (e.contains("R.O.L") || e.contains("ROL") || e.contains("P.I.R") || e.startsWith("RIDUZ")) {
            return Optional.of(TipoRateo.ROL);
        }
        if (e.startsWith("EXF") || e.startsWith("EX-F") || e.startsWith("FEST")) {
            return Optional.of(TipoRateo.EX_FESTIVITA);
        }
        if (e.startsWith("BANCA")) {
            return Optional.of(TipoRateo.BANCA_ORE);
        }
        if (e.startsWith("PERM")) {
            return Optional.of(TipoRateo.PERMESSI);
        }
        return Optional.empty();
    }

    /** Unita' di un rateo stampata ("GG", "GG.", "(GIORNI)", "Ore", "(ORE)", "HH"). */
    static Optional<UnitaMisura> unitaRateo(String testo) {
        var u = maiuscolo(testo).replaceAll("[().\\s]", "");
        return switch (u) {
            case "GG", "GIORNI", "GIORNO", "G" -> Optional.of(UnitaMisura.GIORNI);
            case "ORE", "HH", "H", "ORA" -> Optional.of(UnitaMisura.ORE);
            default -> Optional.empty();
        };
    }

    /**
     * Tipo del cedolino: fine rapporto se il periodo e' successivo al mese di cessazione stampato (e non c'e' una
     * riassunzione), tredicesima/quattordicesima se lo dice l'intestazione o se e' l'unica competenza pagata, aggiuntivo
     * se l'intestazione lo indica ("AGG.") senza tredicesima o quattordicesima, altrimenti ordinaria.
     */
    static TipoCedolinoEstratto tipo(CedolinoLetto c) {
        if (c.periodo != null && c.dataCessazione != null
                && c.periodo.isAfter(YearMonth.from(c.dataCessazione))
                && (c.dataAssunzione == null || !c.dataAssunzione.isAfter(c.dataCessazione))) {
            return TipoCedolinoEstratto.FINE_RAPPORTO;
        }
        var competenze = c.voci.stream()
                .filter(v -> v.categoria() == CategoriaVoce.COMPETENZA && v.importoCompetenza() != null)
                .toList();
        boolean soloTredicesima = !competenze.isEmpty()
                && competenze.stream().allMatch(v -> tredicesima(v.descrizione()));
        boolean soloQuattordicesima = !competenze.isEmpty()
                && competenze.stream().allMatch(v -> quattordicesima(v.descrizione()));
        if (c.intestazioneTredicesima || soloTredicesima) {
            return TipoCedolinoEstratto.TREDICESIMA;
        }
        if (c.intestazioneQuattordicesima || soloQuattordicesima) {
            return TipoCedolinoEstratto.QUATTORDICESIMA;
        }
        if (c.intestazioneAggiuntiva) {
            if (c.contieneTredicesima()) {
                return TipoCedolinoEstratto.TREDICESIMA;
            }
            return c.contieneQuattordicesima() ? TipoCedolinoEstratto.QUATTORDICESIMA : TipoCedolinoEstratto.AGGIUNTIVA;
        }
        return TipoCedolinoEstratto.ORDINARIA;
    }

    /**
     * Cognome e nome da "COGNOME NOME": se il codice fiscale e' noto si sceglie la divisione le cui consonanti
     * corrispondono ai primi sei caratteri del codice (cognomi e nomi composti), altrimenti il cognome e' la prima
     * parola, con le particelle (DE, DI, DEL, LA...).
     */
    static String[] cognomeNome(String testo, String codiceFiscale) {
        if (testo == null || testo.isBlank()) {
            return new String[] {null, null};
        }
        var parole = testo.strip().split("\\s+");
        if (codiceFiscale != null && codiceFiscale.length() == 16 && parole.length > 1) {
            for (int k = 1; k < parole.length; k++) {
                var cognome = String.join(" ", java.util.Arrays.copyOfRange(parole, 0, k));
                var nome = String.join(" ", java.util.Arrays.copyOfRange(parole, k, parole.length));
                if (codiceCognome(cognome).equals(codiceFiscale.substring(0, 3))
                        && codiceNome(nome).equals(codiceFiscale.substring(3, 6))) {
                    return new String[] {cognome, nome};
                }
            }
        }
        return cognomeNome(testo);
    }

    /** Tre caratteri del cognome nel codice fiscale: consonanti, poi vocali, poi X. */
    static String codiceCognome(String cognome) {
        var lettere = lettere(cognome);
        var codice = consonanti(lettere) + vocali(lettere) + "XXX";
        return codice.substring(0, 3);
    }

    /** Tre caratteri del nome nel codice fiscale: con quattro o piu' consonanti la prima, la terza e la quarta. */
    static String codiceNome(String nome) {
        var lettere = lettere(nome);
        var consonanti = consonanti(lettere);
        if (consonanti.length() >= 4) {
            return "" + consonanti.charAt(0) + consonanti.charAt(2) + consonanti.charAt(3);
        }
        return (consonanti + vocali(lettere) + "XXX").substring(0, 3);
    }

    private static String lettere(String testo) {
        var senzaAccenti = java.text.Normalizer.normalize(testo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return maiuscolo(senzaAccenti).replaceAll("[^A-Z]", "");
    }

    private static String consonanti(String lettere) {
        return lettere.replaceAll("[AEIOU]", "");
    }

    private static String vocali(String lettere) {
        return lettere.replaceAll("[^AEIOU]", "");
    }

    /** Unita' di una voce nella forma dello schema ("giorni", "ore", "km", "%", "numero"); null se non stampata. */
    static String unitaVoce(String testo) {
        if (testo == null || testo.isBlank()) {
            return null;
        }
        var u = maiuscolo(testo).replaceAll("[().\\s]", "");
        return switch (u) {
            case "GG", "GIORNI", "GIORNO", "G" -> "giorni";
            case "ORE", "ORA", "HH", "H" -> "ore";
            case "KM" -> "km";
            case "%" -> "%";
            case "N", "NR", "NUM", "NUMERO", "RATEI", "RATEO", "PZ" -> "numero";
            default -> testo.strip().toLowerCase(Locale.ROOT);
        };
    }

    /** Cognome e nome da "COGNOME NOME": il cognome e' la prima parola, con le particelle (DE, DI, DEL, LA...). */
    static String[] cognomeNome(String testo) {
        if (testo == null || testo.isBlank()) {
            return new String[] {null, null};
        }
        var parole = testo.strip().split("\\s+");
        if (parole.length == 1) {
            return new String[] {parole[0], null};
        }
        int fineCognome = 1;
        while (fineCognome < parole.length - 1 && PARTICELLE.matcher(parole[fineCognome - 1]).matches()) {
            fineCognome++;
        }
        var cognome = String.join(" ", java.util.Arrays.copyOfRange(parole, 0, fineCognome));
        var nome = String.join(" ", java.util.Arrays.copyOfRange(parole, fineCognome, parole.length));
        return new String[] {cognome, nome};
    }

    private static final Pattern PARTICELLE = Pattern.compile(
            "(?i)D[AEI]|DEL|DELL[AEO]|DEGLI|DELLE|LA|LO|LE|LI|DAL|DALL[AEO]|DEI|SAN|SANT[AO]?|VAN|VON|MC|MAC|D'|DELL'");

    static String maiuscolo(String testo) {
        return testo == null ? "" : testo.toUpperCase(Locale.ROOT);
    }
}
