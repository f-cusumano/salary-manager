/**
 * Modulo segnalazioni: regole di anomalia (pattern strategy, una classe per regola, registrabili e testate
 * singolarmente) valutate da zero a ogni evento ({@code CedolinoAcquisito}, {@code CedolinoEliminato},
 * {@code CcnlRapportoModificato}), in modo sincrono nella transazione che ha pubblicato l'evento.
 * Principio: ogni segnalazione deve essere veritiera; nel dubbio non si segnala.
 * <p>
 * API pubblica: {@link com.fcusumano.sam.segnalazioni.SegnalazioniRapporto}, {@link com.fcusumano.sam.segnalazioni.Segnalazione}.
 * Tabelle proprie: {@code segnalazione}.
 */
@ApplicationModule(displayName = "Segnalazioni", allowedDependencies = {"shared", "ccnl", "anagrafica", "cedolini"})
package com.fcusumano.sam.segnalazioni;

import org.springframework.modulith.ApplicationModule;
