/**
 * Modulo cedolini: caricamento asincrono dei PDF, lettura dei cedolini (lettore locale, Claude o fixture),
 * normalizzazione, archivio (dati + PDF), eliminazione. Pubblica {@link com.fcusumano.sam.cedolini.CedolinoAcquisito} e
 * {@link com.fcusumano.sam.cedolini.CedolinoEliminato}.
 * <p>
 * API pubblica: {@link com.fcusumano.sam.cedolini.CaricamentoCedolini}, {@link com.fcusumano.sam.cedolini.ArchivioCedolini},
 * {@link com.fcusumano.sam.cedolini.CedolinoNormalizzato} e record collegati.
 * Porte interne: {@code application.estrazione.LettoreCedolini} (adapter locale / Claude / fixture),
 * {@code application.estrazione.LettorePdf} (apertura e decifratura dei PDF),
 * {@code domain.NomeFileCedolinoStrategy}.
 * Tabelle proprie: {@code cedolino}, {@code cedolino_pdf}, {@code cedolino_voce}, {@code cedolino_elemento_retributivo},
 * {@code cedolino_rateo}, {@code cedolino_tfr}, {@code cedolino_dati}, {@code caricamento}.
 */
@ApplicationModule(displayName = "Cedolini", allowedDependencies = {"shared", "ccnl", "anagrafica"})
package com.fcusumano.sam.cedolini;

import org.springframework.modulith.ApplicationModule;
