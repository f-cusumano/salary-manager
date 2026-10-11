package com.fcusumano.sam.shared;

import java.util.List;
import java.util.Objects;

/**
 * Dati della richiesta non validi, con l'elenco dei campi da correggere (tradotta in HTTP 400 con {@code errori} dal
 * modulo web). I messaggi sono mostrati all'utente accanto ai campi: non ripetono mai i valori inviati e non contengono
 * dati personali.
 */
public class DatiNonValidiException extends RuntimeException {

    private final List<ErroreCampo> errori;

    /** @param errori almeno un errore */
    public DatiNonValidiException(List<ErroreCampo> errori) {
        super(riepilogo(errori));
        this.errori = List.copyOf(errori);
    }

    /** Un solo errore, sul campo indicato (null per un errore generale). */
    public DatiNonValidiException(String campo, String messaggio) {
        this(List.of(new ErroreCampo(campo, messaggio)));
    }

    /** Errori nell'ordine dei campi della richiesta. */
    public List<ErroreCampo> errori() {
        return errori;
    }

    private static String riepilogo(List<ErroreCampo> errori) {
        if (errori == null || errori.isEmpty()) {
            throw new IllegalArgumentException("Almeno un errore e' obbligatorio");
        }
        return errori.size() == 1 ? errori.getFirst().messaggio() : errori.size() + " campi da correggere";
    }

    /**
     * Errore su un campo della richiesta.
     *
     * @param campo     percorso nello schema della richiesta (es. "totali.netto", "voci[3].descrizione"), null per un
     *                  errore che non riguarda un campo
     * @param messaggio cosa correggere, senza il valore inviato
     */
    public record ErroreCampo(String campo, String messaggio) {

        public ErroreCampo {
            Objects.requireNonNull(messaggio, "messaggio");
        }
    }
}
