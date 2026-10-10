package com.fcusumano.sam.cedolini.adapter.persistence;

import java.util.List;

/**
 * Adattamento dei testi alla lunghezza delle colonne.
 */
final class Testo {

    private Testo() {
    }

    /** Testo troncato alla lunghezza massima della colonna; null resta null. */
    static String tronca(String valore, int lunghezzaMassima) {
        if (valore == null || valore.length() <= lunghezzaMassima) {
            return valore;
        }
        return valore.substring(0, lunghezzaMassima);
    }

    /**
     * Elenco di righe unite dal separatore entro la lunghezza della colonna: gli a capo interni diventano spazi (ogni
     * elemento resta una riga), una riga troppo lunga e' troncata e le righe che non ci stanno piu' sono omesse,
     * cosi' nessuna riga viene spezzata a meta' in quelle successive. Null se l'elenco e' vuoto.
     */
    static String elenco(List<String> righe, String separatore, int lunghezzaMassima) {
        var risultato = new StringBuilder();
        for (var riga : righe) {
            var pulita = riga == null ? "" : riga.replaceAll("\\R", " ").strip();
            if (pulita.isEmpty()) {
                continue;
            }
            int disponibile = lunghezzaMassima - risultato.length() - (risultato.isEmpty() ? 0 : separatore.length());
            if (disponibile <= 0) {
                break;
            }
            if (!risultato.isEmpty()) {
                risultato.append(separatore);
            }
            risultato.append(tronca(pulita, disponibile));
        }
        return risultato.isEmpty() ? null : risultato.toString();
    }
}
