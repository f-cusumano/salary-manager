package com.fcusumano.sam.cedolini.application.estrazione;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Porta di uscita: verifica e prepara un PDF caricato per l'estrazione (validita', numero di pagine, limiti dell'API,
 * decifratura dei PDF protetti da password di apertura).
 * <p>
 * Ordine delle password provate per un PDF protetto: nessuna password (PDF con sola password del proprietario),
 * password indicata dall'utente per il lotto, password configurate ({@code salary-manager.pdf.password-candidate}),
 * password ricordate (indicate in passato dall'utente), codici fiscali delle persone gia' censite (maiuscoli e
 * minuscoli). Le password non vengono mai loggate.
 */
public interface LettorePdf {

    /** Come {@link #apri(byte[], String, Supplier, Supplier)} senza password ricordate. */
    default DocumentoPdf apri(byte[] contenuto, String passwordUtente,
                              Supplier<? extends Collection<String>> codiciFiscaliCensiti) {
        return apri(contenuto, passwordUtente, List::of, codiciFiscaliCensiti);
    }

    /**
     * Apre il PDF e ne restituisce la versione da passare al lettore dei cedolini.
     *
     * @param contenuto            byte del PDF originale caricato (quello su cui calcolare l'hash)
     * @param passwordUtente       password indicata dall'utente per il lotto, puo' essere null o vuota
     * @param passwordRicordate    fornitore delle password ricordate: invocato solo se il PDF e' protetto e le
     *                             password dell'utente e configurate non funzionano
     * @param codiciFiscaliCensiti fornitore dei codici fiscali delle persone del workspace: invocato solo se il PDF e'
     *                             protetto e le altre password non funzionano
     * @return documento pronto per l'estrazione
     * @throws PdfNonLeggibileException se il file non e' un PDF valido, e' protetto da una password sconosciuta
     *                                  ({@link PdfNonLeggibileException#MESSAGGIO_PASSWORD}) o supera i limiti dell'API
     */
    DocumentoPdf apri(byte[] contenuto, String passwordUtente, Supplier<? extends Collection<String>> passwordRicordate,
                      Supplier<? extends Collection<String>> codiciFiscaliCensiti);

    /**
     * Toglie la protezione a un PDF gia' archiviato, senza i controlli sui limiti dell'estrazione.
     *
     * @param password fornitore delle password da provare oltre a quelle configurate (ricordate, codici fiscali):
     *                 invocato solo se il PDF richiede una password di apertura
     */
    Sblocco sblocca(byte[] contenuto, Supplier<? extends Collection<String>> password);

    /**
     * PDF pronto per l'estrazione.
     *
     * @param contenutoPerEstrazione byte da passare al lettore dei cedolini e da archiviare: l'originale se non cifrato,
     *                               altrimenti una copia decifrata (stesse pagine e contenuto, senza alcuna protezione)
     * @param numeroPagine           pagine del documento
     * @param protetto               true se il PDF originale era cifrato con password di apertura
     * @param decifrato              true se {@code contenutoPerEstrazione} e' una copia senza protezione (PDF cifrato,
     *                               anche con la sola password del proprietario): e' la copia da archiviare
     * @param passwordUtenteValida   la password indicata dall'utente (o la sua versione senza spazi ai lati) se e'
     *                               quella che ha aperto il PDF, altrimenti null: e' la password da ricordare
     */
    record DocumentoPdf(byte[] contenutoPerEstrazione, int numeroPagine, boolean protetto, boolean decifrato,
                        String passwordUtenteValida) {

        public DocumentoPdf(byte[] contenutoPerEstrazione, int numeroPagine, boolean protetto) {
            this(contenutoPerEstrazione, numeroPagine, protetto, protetto, null);
        }

        /** Solo pagine e flag: mai la password. */
        @Override
        public String toString() {
            return "DocumentoPdf[pagine=" + numeroPagine + ", protetto=" + protetto + ", decifrato=" + decifrato + "]";
        }
    }

    /** Esito dello sblocco di un PDF archiviato. */
    sealed interface Sblocco {

        /** Il PDF non ha alcuna protezione: va bene com'e'. */
        record NonProtetto() implements Sblocco {
        }

        /** Copia senza protezione (stesse pagine e contenuto) da archiviare al posto dell'originale. */
        record Sbloccato(byte[] contenuto) implements Sblocco {
        }

        /** Nessuna password nota apre il PDF. */
        record Protetto() implements Sblocco {
        }

        /** Il PDF non si apre per altri motivi (danneggiato, non PDF): resta com'e'. */
        record NonLeggibile() implements Sblocco {
        }
    }
}
