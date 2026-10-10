package com.fcusumano.sam.cedolini.application.estrazione;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Porta di uscita: verifica e prepara un PDF caricato per l'estrazione (validita', numero di pagine, limiti dell'API,
 * decifratura dei PDF protetti da password di apertura).
 * <p>
 * Ordine delle password provate per un PDF protetto: nessuna password (PDF con sola password del proprietario),
 * password indicata dall'utente per il lotto, password configurate ({@code salary-manager.pdf.password-candidate}),
 * codici fiscali delle persone gia' censite (maiuscoli e minuscoli). Le password non vengono mai loggate.
 */
public interface LettorePdf {

    /**
     * Apre il PDF e ne restituisce la versione da passare al lettore dei cedolini.
     *
     * @param contenuto            byte del PDF originale caricato (quello da archiviare e su cui calcolare l'hash)
     * @param passwordUtente       password indicata dall'utente per il lotto, puo' essere null o vuota
     * @param codiciFiscaliCensiti fornitore dei codici fiscali delle persone del workspace: invocato solo se il PDF e'
     *                             protetto e le altre password non funzionano
     * @return documento pronto per l'estrazione
     * @throws PdfNonLeggibileException se il file non e' un PDF valido, e' protetto da una password sconosciuta
     *                                  ({@link PdfNonLeggibileException#MESSAGGIO_PASSWORD}) o supera i limiti dell'API
     */
    DocumentoPdf apri(byte[] contenuto, String passwordUtente, Supplier<? extends Collection<String>> codiciFiscaliCensiti);

    /**
     * PDF pronto per l'estrazione.
     *
     * @param contenutoPerEstrazione byte da passare al lettore dei cedolini: l'originale se non protetto, altrimenti una copia
     *                               decifrata (senza alcuna protezione). Non va archiviato: a DB si salva l'originale
     * @param numeroPagine           pagine del documento
     * @param protetto               true se il PDF originale era cifrato con password di apertura
     */
    record DocumentoPdf(byte[] contenutoPerEstrazione, int numeroPagine, boolean protetto) {
    }
}
