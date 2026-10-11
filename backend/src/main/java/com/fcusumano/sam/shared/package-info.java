/**
 * Kernel condiviso: tipi di valore usati da tutti i moduli (workspace, competenza, unita' di misura,
 * utilita' per importi e periodi), eccezioni applicative comuni (risorsa non trovata, operazione non consentita, dati
 * non validi campo per campo: {@link com.fcusumano.sam.shared.DatiNonValidiException}), configurazione comune
 * ({@code config}: orologio, jOOQ, lettura del file {@code .env} e dei segreti) e codice jOOQ generato
 * ({@code com.fcusumano.sam.shared.db.jooq}).
 * <p>
 * Modulo OPEN: tutti i suoi package sono accessibili agli altri moduli. Ogni modulo usa pero'
 * solo le classi jOOQ delle tabelle di cui e' proprietario (verificato da ArchitectureTests).
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package com.fcusumano.sam.shared;

import org.springframework.modulith.ApplicationModule;
