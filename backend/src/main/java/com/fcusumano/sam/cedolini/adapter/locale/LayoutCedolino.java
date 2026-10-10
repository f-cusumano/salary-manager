package com.fcusumano.sam.cedolini.adapter.locale;

/**
 * Strategia di lettura di un layout di cedolino (un software paghe o un modulo prestampato). Il lettore locale prova i
 * layout in ordine e usa il primo che riconosce il documento.
 * <p>
 * Regole per chi scrive un layout (vedi anche {@code docs/sviluppo.md}):
 * <ul>
 *   <li>{@link #riconosce} deve essere stretto: poche etichette caratteristiche che nessun altro layout stampa;</li>
 *   <li>{@link #leggi} lascia null cio' che non trova (mai valori dedotti o inventati), segnala con
 *       {@link CedolinoLetto#etichettaMancante} le sezioni attese e assenti e con
 *       {@link CedolinoLetto#controlloFallito} i controlli interni non superati;</li>
 *   <li>non legge mai i dati bancari (IBAN, ABI, CAB, conto) e non logga valori letti.</li>
 * </ul>
 */
interface LayoutCedolino {

    /** Identificativo stabile del layout: finisce nel modello registrato con il cedolino ("lettore-locale-1/id"). */
    String id();

    /** Nome leggibile del layout, per messaggi e documentazione. */
    String nome();

    /** True se il documento stampa le etichette caratteristiche del layout. */
    boolean riconosce(DocumentoTesto documento);

    /** Dati del cedolino letti dal documento (riconosciuto da {@link #riconosce}). */
    CedolinoLetto leggi(DocumentoTesto documento);
}
