package com.fcusumano.sam.anagrafica.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizzazione della ragione sociale per il matching dell'azienda: maiuscolo senza accenti, punteggiatura e spazi
 * multipli, sigle puntate ricompattate e forme societarie uniformate. Esempi: "Alfa Software S.r.l.",
 * "ALFA SOFTWARE SRL", "Alfa Software - S.R.L." e "ALFA SOFTWARE S.R.L.S.U." diventano tutte "ALFA SOFTWARE SRL".
 */
public final class RagioneSociale {

    /** Lunghezza massima memorizzabile (colonne {@code ragione_sociale} e {@code ragione_sociale_normalizzata}). */
    public static final int LUNGHEZZA = 200;

    private static final Pattern PUNTI = Pattern.compile("\\.");
    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^A-Z0-9]+");

    /**
     * Forme societarie (sequenze di parole gia' normalizzate) e forma canonica. L'ordine conta: le sequenze piu'
     * lunghe vanno prima di quelle che contengono.
     */
    private static final List<Forma> FORME = List.of(
            forma("SCARL", "SOCIETA COOPERATIVA A RESPONSABILITA LIMITATA"),
            forma("SCARL", "SOC COOP A RESPONSABILITA LIMITATA"),
            forma("SCARL", "SOC COOP A RL"),
            forma("SCARL", "SOC COOP ARL"),
            forma("SCARL", "SC A RL"),
            forma("SCARL", "SCRL"),
            forma("SC", "SOCIETA COOPERATIVA"),
            forma("SC", "SOC COOP"),
            forma("SRL", "SOCIETA A RESPONSABILITA LIMITATA SEMPLIFICATA"),
            forma("SRL", "SOCIETA A RESPONSABILITA LIMITATA"),
            forma("SRL", "SRLSU"),
            forma("SRL", "SRLS"),
            forma("SRL", "SRLU"),
            forma("SAPA", "SOCIETA IN ACCOMANDITA PER AZIONI"),
            forma("SPA", "SOCIETA PER AZIONI"),
            forma("SPA", "SPASU"),
            forma("SAS", "SOCIETA IN ACCOMANDITA SEMPLICE"),
            forma("SNC", "SOCIETA IN NOME COLLETTIVO"),
            forma("SS", "SOCIETA SEMPLICE"));

    /** Indicazioni che non identificano l'azienda (socio unico, liquidazione) e vengono rimosse. */
    private static final List<List<String>> DA_RIMUOVERE = List.of(
            parole("A SOCIO UNICO"),
            parole("CON SOCIO UNICO"),
            parole("SOCIO UNICO"),
            parole("UNIPERSONALE"),
            parole("IN LIQUIDAZIONE"));

    /** Sigle societarie che possono comparire puntate e spaziate. */
    private static final Set<String> SIGLE = Set.of(
            "SRL", "SRLS", "SRLSU", "SRLU", "SPA", "SPASU", "SAPA", "SAS", "SNC", "SS", "SC", "SCARL", "SCRL", "ARL", "SU");

    /** Forme canoniche dopo le quali un "SU" isolato significa "socio unico". */
    private static final List<String> FORME_CON_SOCIO_UNICO = List.of("SRL", "SPA");

    private RagioneSociale() {
    }

    public static String normalizza(String ragioneSociale) {
        if (ragioneSociale == null || ragioneSociale.isBlank()) {
            return null;
        }
        var maiuscolo = Testi.maiuscoloSenzaAccenti(ragioneSociale).replace("&", " E ");
        var senzaPunti = PUNTI.matcher(maiuscolo).replaceAll("");
        var testo = NON_ALFANUMERICI.matcher(senzaPunti).replaceAll(" ").strip();
        if (testo.isEmpty()) {
            return null;
        }
        var parole = rimuovi(Arrays.asList(testo.split(" ")));
        parole = ricompattaSigle(parole);
        parole = rimuovi(parole);
        parole = uniformaForme(parole);
        parole = rimuoviSocioUnico(parole);
        var normalizzata = String.join(" ", parole);
        return normalizzata.length() > LUNGHEZZA ? normalizzata.substring(0, LUNGHEZZA).strip() : normalizzata;
    }

    /**
     * Sigle societarie puntate e spaziate ("S. R. L.") ricompattate: in una sequenza di lettere singole si unisce la
     * coda piu' lunga che forma una sigla nota ("S R L" diventa "SRL"); le altre lettere restano separate
     * ("Rossi & C." resta "ROSSI E C").
     */
    private static List<String> ricompattaSigle(List<String> parole) {
        var risultato = new ArrayList<String>();
        var lettere = new ArrayList<String>();
        for (var parola : parole) {
            if (parola.length() == 1 && Character.isLetter(parola.charAt(0))) {
                lettere.add(parola);
            } else {
                chiudiSigla(lettere, risultato);
                risultato.add(parola);
            }
        }
        chiudiSigla(lettere, risultato);
        return risultato;
    }

    private static void chiudiSigla(List<String> lettere, List<String> risultato) {
        int inizioSigla = lettere.size();
        for (int i = 0; i < lettere.size() - 1; i++) {
            if (SIGLE.contains(String.join("", lettere.subList(i, lettere.size())))) {
                inizioSigla = i;
                break;
            }
        }
        risultato.addAll(lettere.subList(0, inizioSigla));
        if (inizioSigla < lettere.size()) {
            risultato.add(String.join("", lettere.subList(inizioSigla, lettere.size())));
        }
        lettere.clear();
    }

    private static List<String> rimuovi(List<String> parole) {
        var risultato = new ArrayList<>(parole);
        for (var sequenza : DA_RIMUOVERE) {
            int indice;
            while ((indice = indiceDi(risultato, sequenza)) >= 0) {
                risultato.subList(indice, indice + sequenza.size()).clear();
            }
        }
        return risultato;
    }

    private static List<String> uniformaForme(List<String> parole) {
        var risultato = new ArrayList<>(parole);
        for (var forma : FORME) {
            int indice;
            while ((indice = indiceDi(risultato, forma.parole())) >= 0) {
                var sottolista = risultato.subList(indice, indice + forma.parole().size());
                sottolista.clear();
                sottolista.add(forma.canonica());
            }
        }
        return risultato;
    }

    private static List<String> rimuoviSocioUnico(List<String> parole) {
        var risultato = new ArrayList<String>();
        for (var parola : parole) {
            boolean socioUnico = parola.equals("SU") && !risultato.isEmpty()
                    && FORME_CON_SOCIO_UNICO.contains(risultato.getLast());
            if (!socioUnico) {
                risultato.add(parola);
            }
        }
        return risultato;
    }

    private static int indiceDi(List<String> parole, List<String> sequenza) {
        for (int i = 0; i + sequenza.size() <= parole.size(); i++) {
            if (parole.subList(i, i + sequenza.size()).equals(sequenza)) {
                return i;
            }
        }
        return -1;
    }

    private static Forma forma(String canonica, String parole) {
        return new Forma(canonica, parole(parole));
    }

    private static List<String> parole(String testo) {
        return List.of(testo.split(" "));
    }

    private record Forma(String canonica, List<String> parole) {
    }
}
