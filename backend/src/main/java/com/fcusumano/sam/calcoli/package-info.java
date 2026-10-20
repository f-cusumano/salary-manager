/**
 * Modulo calcoli: dati derivati di un rapporto (anzianita', scatti, RAL, TFR, ratei con stima del valore, indicatori
 * per mensilita'), ricalcolati da zero in modo deterministico e idempotente a ogni evento
 * ({@code CedolinoAcquisito}, {@code CedolinoEliminato}, {@code CcnlRapportoModificato}), in modo sincrono nella
 * transazione che ha pubblicato l'evento. Il preavviso del rapporto in corso e' calcolato in lettura (CCNL, livello e
 * riepilogo salvato), cosi' segue subito la tabella dei termini verificati.
 * <p>
 * API pubblica: {@link com.fcusumano.sam.calcoli.CalcoliRapporto}, {@link com.fcusumano.sam.calcoli.RiepilogoRapporto},
 * {@link com.fcusumano.sam.calcoli.Preavviso}.
 * Tabelle proprie: {@code riepilogo_rapporto}, {@code riepilogo_rateo}, {@code indicatore_mensilita}.
 */
@ApplicationModule(displayName = "Calcoli", allowedDependencies = {"shared", "ccnl", "anagrafica", "cedolini"})
package com.fcusumano.sam.calcoli;

import org.springframework.modulith.ApplicationModule;
