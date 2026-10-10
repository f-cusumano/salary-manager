/**
 * Dominio del modulo segnalazioni (Java puro): {@link com.fcusumano.sam.segnalazioni.domain.RegolaSegnalazione}
 * (strategy), le singole regole (una classe per tipo di segnalazione, testata singolarmente con casi positivi e
 * negativi) e {@link com.fcusumano.sam.segnalazioni.domain.MotoreSegnalazioni}, che le valuta da zero, elimina i
 * duplicati e ordina il risultato.
 * <p>
 * Principio comune: si segnala solo cio' che i dati presenti dimostrano; se un dato necessario manca o e' ambiguo la
 * regola non segnala. Testi in italiano con valori formattati ({@link com.fcusumano.sam.segnalazioni.domain.Formato}).
 */
package com.fcusumano.sam.segnalazioni.domain;
