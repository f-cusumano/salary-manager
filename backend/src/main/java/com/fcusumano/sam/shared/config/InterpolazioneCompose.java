package com.fcusumano.sam.shared.config;

import java.util.function.UnaryOperator;

/**
 * Escape e sostituzione delle variabili nei valori di un file {@code .env}, come li applica Docker Compose: gli escape
 * solo tra virgolette doppie, la sostituzione ({@code $NOME}, {@code ${NOME}}, {@code ${NOME:-default}} e le altre
 * forme) nei valori senza virgolette e tra virgolette doppie. Usata da {@link CaricatoreFileEnv}.
 * <p>
 * Unica differenza voluta: Compose sostituisce una variabile non definita con un testo vuoto (con un solo avviso),
 * qui e' un errore, cosi' una password con un {@code $} non viene troncata in silenzio. I messaggi d'errore non
 * riportano il valore, che puo' essere un segreto.
 */
final class InterpolazioneCompose {

    private static final String SUGGERIMENTO = ": per un '$' letterale scrivi '$$' oppure metti il valore tra apici "
            + "singoli";

    private InterpolazioneCompose() {
    }

    /**
     * Escape del testo tra virgolette doppie (gia' senza le virgolette, con {@code \"} gia' risolto): {@code \a},
     * {@code \b}, {@code \f}, {@code \n}, {@code \r}, {@code \t}, {@code \v}, {@code \\}, {@code \"}, {@code \$}
     * (un {@code $} che la sostituzione lascia letterale) e {@code \0} seguito da 3 cifre ottali. Ogni altra
     * {@code \} resta com'e' (<code>&#92;u</code> e {@code \x} compresi); come in Compose, uno {@code \0} senza 3 cifre
     * ottali perde lo zero.
     */
    static String espandiEscape(String testo) {
        var risultato = new StringBuilder(testo.length());
        int i = 0;
        while (i < testo.length()) {
            char c = testo.charAt(i);
            if (c != '\\' || i + 1 == testo.length()) {
                risultato.append(c);
                i++;
                continue;
            }
            char dopo = testo.charAt(i + 1);
            switch (dopo) {
                case 'a' -> risultato.append('\u0007');
                case 'b' -> risultato.append('\b');
                case 'f' -> risultato.append('\f');
                case 'n' -> risultato.append('\n');
                case 'r' -> risultato.append('\r');
                case 't' -> risultato.append('\t');
                case 'v' -> risultato.append('\u000B');
                case '\\', '"' -> risultato.append(dopo);
                case '$' -> risultato.append("$$");
                case '0' -> {
                    i = ottale(testo, i, risultato);
                    continue;
                }
                default -> {
                    risultato.append(c);
                    i++;
                    continue;
                }
            }
            i += 2;
        }
        return risultato.toString();
    }

    /** {@code \0} seguito da al massimo 3 cifre alla posizione {@code i}; restituisce la posizione successiva. */
    private static int ottale(String testo, int i, StringBuilder risultato) {
        int fine = i + 2;
        while (fine < testo.length() && fine < i + 5 && testo.charAt(fine) >= '0' && testo.charAt(fine) <= '9') {
            fine++;
        }
        var cifre = testo.substring(i + 2, fine);
        if (cifre.length() == 3 && cifre.chars().allMatch(d -> d <= '7') && Integer.parseInt(cifre, 8) <= 0xFF) {
            risultato.append((char) Integer.parseInt(cifre, 8));
        } else {
            risultato.append('\\').append(cifre);
        }
        return fine;
    }

    /**
     * Sostituzione delle variabili come in Compose: {@code $$} diventa {@code $}; {@code $NOME} e {@code ${NOME}} il
     * valore della variabile; {@code ${NOME:-x}} e {@code ${NOME-x}} il default se la variabile e' vuota o non
     * definita (solo non definita nella forma senza {@code :}); {@code ${NOME:+x}} e {@code ${NOME+x}} il testo
     * alternativo se e' non vuota (definita); {@code ${NOME:?msg}} e {@code ${NOME?msg}} un errore se e' vuota o non
     * definita (non definita). Default e testi alternativi possono contenere altre sostituzioni. Un {@code $} non
     * seguito da {@code $}, da un nome o da <code>{</code> resta com'e'.
     *
     * @param variabili valore di una variabile, {@code null} se non definita
     * @throws IllegalArgumentException con la descrizione del problema, senza il valore
     */
    static String sostituisci(String testo, UnaryOperator<String> variabili) {
        return new Sostituzione(variabili, false).sostituisci(testo);
    }

    /**
     * @param tollerante se vero una variabile non definita vale come testo vuoto (per verificare la sintassi di un
     *                   default che non si usa, come fa Compose)
     */
    private record Sostituzione(UnaryOperator<String> variabili, boolean tollerante) {

        String sostituisci(String testo) {
            var risultato = new StringBuilder(testo.length());
            int i = 0;
            while (i < testo.length()) {
                char c = testo.charAt(i);
                if (c != '$' || i + 1 == testo.length()) {
                    risultato.append(c);
                    i++;
                } else if (testo.charAt(i + 1) == '$') {
                    risultato.append('$');
                    i += 2;
                } else if (inizioNome(testo.charAt(i + 1))) {
                    int fine = fineNome(testo, i + 1);
                    risultato.append(valore(testo.substring(i + 1, fine)));
                    i = fine;
                } else if (testo.charAt(i + 1) == '{') {
                    i = graffe(testo, i, risultato);
                } else {
                    risultato.append(c);
                    i++;
                }
            }
            return risultato.toString();
        }

        /** Sostituzione {@code ${...}} che inizia in {@code inizio}; restituisce la posizione successiva. */
        private int graffe(String testo, int inizio, StringBuilder risultato) {
            int inizioNome = inizio + 2;
            if (inizioNome == testo.length() || !inizioNome(testo.charAt(inizioNome))) {
                throw nonValida();
            }
            int fineNome = fineNome(testo, inizioNome);
            var nome = testo.substring(inizioNome, fineNome);
            if (fineNome < testo.length() && testo.charAt(fineNome) == '}') {
                risultato.append(valore(nome));
                return fineNome + 1;
            }
            var operatore = operatore(testo, fineNome);
            int chiusura = chiusura(testo, inizio, fineNome);
            var argomento = testo.substring(fineNome + operatore.length(), chiusura);
            var definita = variabili.apply(nome);
            boolean vuota = definita == null || (operatore.startsWith(":") && definita.isEmpty());
            risultato.append(switch (operatore.substring(operatore.length() - 1)) {
                case "-" -> vuota ? sostituisci(argomento) : verificata(definita, argomento);
                case "+" -> vuota ? verificata("", argomento) : sostituisci(argomento);
                default -> {
                    if (vuota && !tollerante) {
                        throw new IllegalArgumentException("variabile obbligatoria (${NOME:?...} o ${NOME?...}) "
                                + "non definita");
                    }
                    yield definita == null ? "" : definita;
                }
            });
            return chiusura + 1;
        }

        /** {@code valore}, dopo aver verificato la sintassi del testo che non si usa. */
        private String verificata(String valore, String nonUsato) {
            new Sostituzione(variabili, true).sostituisci(nonUsato);
            return valore;
        }

        private String valore(String nome) {
            var valore = variabili.apply(nome);
            if (valore == null && !tollerante) {
                throw new IllegalArgumentException("riferimento a una variabile non definita" + SUGGERIMENTO);
            }
            return valore == null ? "" : valore;
        }

        private static String operatore(String testo, int pos) {
            for (var operatore : new String[] {":-", ":+", ":?", "-", "+", "?"}) {
                if (testo.startsWith(operatore, pos)) {
                    return operatore;
                }
            }
            throw nonValida();
        }

        /**
         * Graffa che chiude la sostituzione aperta in {@code inizio}, contata come Compose: ogni <code>{</code> apre un
         * livello (il carattere che la segue non si guarda), ogni <code>}</code> lo chiude; se i livelli non tornano a
         * zero vale l'ultima graffa chiusa del testo.
         */
        private static int chiusura(String testo, int inizio, int fineNome) {
            int aperte = 0;
            for (int i = inizio; i < testo.length(); i++) {
                char c = testo.charAt(i);
                if (c == '}' && --aperte == 0) {
                    return i;
                }
                if (c == '{') {
                    aperte++;
                    i++;
                }
            }
            int ultima = testo.lastIndexOf('}');
            if (ultima < fineNome) {
                throw nonValida();
            }
            return ultima;
        }

        private static IllegalArgumentException nonValida() {
            return new IllegalArgumentException("sostituzione ${...} non valida" + SUGGERIMENTO);
        }

        private static boolean inizioNome(char c) {
            return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static int fineNome(String testo, int inizio) {
            int fine = inizio;
            while (fine < testo.length() && (inizioNome(testo.charAt(fine))
                    || (testo.charAt(fine) >= '0' && testo.charAt(fine) <= '9'))) {
                fine++;
            }
            return fine;
        }
    }
}
