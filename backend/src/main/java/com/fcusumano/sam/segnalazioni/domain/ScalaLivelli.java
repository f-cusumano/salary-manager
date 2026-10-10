package com.fcusumano.sam.segnalazioni.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Scala dei livelli di inquadramento di un CCNL, definita solo dove il verso e' certo:
 * <ul>
 *   <li><b>COMMERCIO</b> (Terziario Confcommercio): livelli 7..1 e Quadro; un numero piu' basso indica un livello
 *       piu' alto, il Quadro e' il piu' alto;</li>
 *   <li><b>METALMECCANICO_INDUSTRIA</b>: classificazione numerica storica 1..8 (8 = quadri, "5S" = quinto super),
 *       in cui un numero piu' alto indica un livello piu' alto, e classificazione per lettere introdotta dal rinnovo
 *       2021 (D1, D2, C1, C2, C3, B1, B2, B3, A1, dal piu' basso al piu' alto). Le due classificazioni non si
 *       confrontano tra loro.</li>
 * </ul>
 * Per gli altri CCNL il verso non e' certo (o la scala non e' uniforme) e non si definisce alcuna scala.
 */
interface ScalaLivelli {

    /** Spiegazione del verso della scala per la descrizione della segnalazione. */
    String verso();

    /** Grado del livello stampato; vuoto se il testo non e' riconosciuto con certezza. */
    Optional<Grado> grado(String livelloStampato);

    /**
     * Posizione di un livello nella scala.
     *
     * @param classificazione gradi confrontabili solo se della stessa classificazione
     * @param rango           piu' alto = livello piu' alto
     */
    record Grado(String classificazione, int rango) {
    }

    /** Scala del CCNL indicato, se il verso e' certo. */
    static Optional<ScalaLivelli> per(String codiceCcnl) {
        return Optional.ofNullable(SCALE.get(codiceCcnl));
    }

    /** Testo del livello senza parole e simboli accessori: "5' Livello" -> "5", "Liv. 3 S" -> "3S". */
    static String normalizza(String livello) {
        if (livello == null) {
            return "";
        }
        return livello.toUpperCase(Locale.ROOT)
                .replaceAll("LIVELLO|LIV\\.?", "")
                .replaceAll("[\\s°º'’^.]", "");
    }

    Map<String, ScalaLivelli> SCALE = Map.of(
            "COMMERCIO", new Commercio(),
            "METALMECCANICO_INDUSTRIA", new Metalmeccanico());

    /** CCNL Commercio: 7 (piu' basso) .. 1, Quadro (piu' alto). */
    final class Commercio implements ScalaLivelli {

        private static final Pattern NUMERO = Pattern.compile("[1-7]");
        private static final Pattern QUADRO = Pattern.compile("Q|QUADRO|QUADRI");

        @Override
        public String verso() {
            return "per il CCNL Commercio un numero più basso indica un livello più alto e il Quadro è il livello più alto";
        }

        @Override
        public Optional<Grado> grado(String livelloStampato) {
            var livello = ScalaLivelli.normalizza(livelloStampato);
            if (QUADRO.matcher(livello).matches()) {
                return Optional.of(new Grado("COMMERCIO", 8));
            }
            if (NUMERO.matcher(livello).matches()) {
                return Optional.of(new Grado("COMMERCIO", 8 - Integer.parseInt(livello)));
            }
            return Optional.empty();
        }
    }

    /** CCNL Metalmeccanico industria: classificazione numerica storica e classificazione per lettere (2021). */
    final class Metalmeccanico implements ScalaLivelli {

        private static final Pattern NUMERICA = Pattern.compile("([1-8])(S|SUPER)?");
        private static final List<String> LETTERE = List.of("D1", "D2", "C1", "C2", "C3", "B1", "B2", "B3", "A1");

        @Override
        public String verso() {
            return "per il CCNL Metalmeccanico industria un livello con numero più alto è superiore e, nella "
                    + "classificazione per lettere, l'ordine va da D1 (più basso) ad A1 (più alto)";
        }

        @Override
        public Optional<Grado> grado(String livelloStampato) {
            var livello = ScalaLivelli.normalizza(livelloStampato);
            int indice = LETTERE.indexOf(livello);
            if (indice >= 0) {
                return Optional.of(new Grado("LETTERE", indice));
            }
            var numerica = NUMERICA.matcher(livello);
            if (numerica.matches()) {
                int rango = Integer.parseInt(numerica.group(1)) * 2 + (numerica.group(2) != null ? 1 : 0);
                return Optional.of(new Grado("NUMERICA", rango));
            }
            return Optional.empty();
        }
    }
}
