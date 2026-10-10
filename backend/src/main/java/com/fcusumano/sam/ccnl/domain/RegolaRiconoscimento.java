package com.fcusumano.sam.ccnl.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Regola di riconoscimento di un CCNL dal testo stampato sul cedolino.
 *
 * @param codice        codice del CCNL
 * @param codiceCnel    codice dell'archivio CNEL (es. "C011"); null se non verificato
 * @param paroleChiave  frasi (una o piu' parole intere) che indicano il CCNL
 * @param paroleEscluse frasi che, se presenti nel testo, escludono il CCNL (es. "artigianato" per un CCNL industriale)
 */
public record RegolaRiconoscimento(
        String codice,
        String codiceCnel,
        List<TestoNormalizzato> paroleChiave,
        List<TestoNormalizzato> paroleEscluse) {

    /** Separatore delle frasi nelle colonne {@code parole_chiave} e {@code parole_escluse}. */
    public static final String SEPARATORE = "|";

    public RegolaRiconoscimento {
        Objects.requireNonNull(codice, "codice");
        codiceCnel = codiceCnel == null || codiceCnel.isBlank() ? null : codiceCnel.strip().toUpperCase(Locale.ROOT);
        paroleChiave = List.copyOf(paroleChiave);
        paroleEscluse = List.copyOf(paroleEscluse);
    }

    /** Crea la regola a partire dalle colonne della tabella (frasi separate da "|", null = nessuna). */
    public static RegolaRiconoscimento daColonne(String codice, String codiceCnel, String paroleChiave, String paroleEscluse) {
        return new RegolaRiconoscimento(codice, codiceCnel, frasi(paroleChiave), frasi(paroleEscluse));
    }

    private static List<TestoNormalizzato> frasi(String elenco) {
        if (elenco == null || elenco.isBlank()) {
            return List.of();
        }
        return Arrays.stream(elenco.split(Pattern.quote(SEPARATORE)))
                .map(TestoNormalizzato::di)
                .filter(frase -> !frase.vuoto())
                .toList();
    }
}
