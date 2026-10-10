package com.fcusumano.sam.ccnl.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Riconoscimento conservativo del CCNL dal testo stampato sul cedolino.
 * <ol>
 *   <li>il testo viene normalizzato (minuscolo, senza accenti e punteggiatura) e confrontato per parole intere;</li>
 *   <li>un CCNL e' candidato se almeno una sua frase chiave compare nel testo e nessuna delle sue frasi escluse vi
 *       compare;</li>
 *   <li>una corrispondenza contenuta in una corrispondenza piu' lunga di un altro CCNL non conta (es. "metalmeccanica"
 *       dentro "piccola e media industria metalmeccanica");</li>
 *   <li>se nel testo c'e' un codice CNEL presente in tabella, deve indicare lo stesso CCNL delle parole chiave
 *       (o essere l'unico indizio): in caso di contrasto non si riconosce nulla.</li>
 * </ol>
 * Il risultato e' un codice solo se il candidato e' unico; in ogni caso di dubbio e' vuoto.
 */
public final class RiconoscitoreCcnl {

    /** Codice CNEL: una lettera e tre cifre (es. "C011"), cercato come parola intera. */
    private static final Pattern CODICE_CNEL = Pattern.compile("[a-z][0-9]{3}");

    private final List<RegolaRiconoscimento> regole;

    public RiconoscitoreCcnl(List<RegolaRiconoscimento> regole) {
        this.regole = List.copyOf(regole);
    }

    public Optional<String> riconosci(String testoStampato) {
        var testo = TestoNormalizzato.di(testoStampato);
        if (testo.vuoto()) {
            return Optional.empty();
        }
        var daCodiceCnel = candidatiDaCodiceCnel(testo);
        if (daCodiceCnel.size() > 1) {
            return Optional.empty();
        }
        var daParoleChiave = candidatiDaParoleChiave(testo);
        if (daParoleChiave.size() > 1) {
            return Optional.empty();
        }
        if (daCodiceCnel.isEmpty()) {
            return daParoleChiave.stream().findFirst();
        }
        var cnel = daCodiceCnel.iterator().next();
        if (daParoleChiave.isEmpty() || daParoleChiave.contains(cnel)) {
            return escluso(cnel, testo) ? Optional.empty() : Optional.of(cnel);
        }
        return Optional.empty();
    }

    private Set<String> candidatiDaCodiceCnel(TestoNormalizzato testo) {
        var codiciNelTesto = testo.parole().stream()
                .filter(parola -> CODICE_CNEL.matcher(parola).matches())
                .map(parola -> parola.toUpperCase(Locale.ROOT))
                .toList();
        var candidati = new LinkedHashSet<String>();
        for (var regola : regole) {
            if (regola.codiceCnel() != null && codiciNelTesto.contains(regola.codiceCnel())) {
                candidati.add(regola.codice());
            }
        }
        return candidati;
    }

    private Set<String> candidatiDaParoleChiave(TestoNormalizzato testo) {
        Map<String, List<Intervallo>> corrispondenze = new LinkedHashMap<>();
        for (var regola : regole) {
            if (escluso(regola.codice(), testo)) {
                continue;
            }
            var intervalli = new ArrayList<Intervallo>();
            for (var frase : regola.paroleChiave()) {
                for (int inizio : testo.occorrenze(frase)) {
                    intervalli.add(new Intervallo(inizio, inizio + frase.parole().size()));
                }
            }
            if (!intervalli.isEmpty()) {
                corrispondenze.put(regola.codice(), intervalli);
            }
        }
        var candidati = new LinkedHashSet<String>();
        corrispondenze.forEach((codice, intervalli) -> {
            boolean almenoUnaNonDominata = intervalli.stream()
                    .anyMatch(intervallo -> !dominato(intervallo, codice, corrispondenze));
            if (almenoUnaNonDominata) {
                candidati.add(codice);
            }
        });
        return candidati;
    }

    private static boolean dominato(Intervallo intervallo, String codice, Map<String, List<Intervallo>> corrispondenze) {
        return corrispondenze.entrySet().stream()
                .filter(altro -> !altro.getKey().equals(codice))
                .flatMap(altro -> altro.getValue().stream())
                .anyMatch(altro -> altro.contieneStrettamente(intervallo));
    }

    private boolean escluso(String codice, TestoNormalizzato testo) {
        return regole.stream()
                .filter(regola -> regola.codice().equals(codice))
                .flatMap(regola -> regola.paroleEscluse().stream())
                .anyMatch(testo::contiene);
    }

    /** Intervallo di parole [inizio, fine) del testo. */
    private record Intervallo(int inizio, int fine) {

        boolean contieneStrettamente(Intervallo altro) {
            return inizio <= altro.inizio && fine >= altro.fine && (fine - inizio) > (altro.fine - altro.inizio);
        }
    }
}
