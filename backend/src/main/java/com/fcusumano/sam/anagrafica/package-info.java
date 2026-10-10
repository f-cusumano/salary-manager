/**
 * Modulo anagrafica: persone, aziende e rapporti di lavoro persona-azienda.
 * <p>
 * API pubblica (package radice): {@link com.fcusumano.sam.anagrafica.CensimentoAnagrafica} (usata dal modulo
 * cedolini durante l'acquisizione), {@link com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica},
 * {@link com.fcusumano.sam.anagrafica.GestioneRapporti} (note e CCNL scelti dall'utente), record ed eventi.
 * Tabelle proprie: {@code persona}, {@code azienda}, {@code rapporto_lavoro}.
 */
@ApplicationModule(displayName = "Anagrafica", allowedDependencies = {"shared", "ccnl"})
package com.fcusumano.sam.anagrafica;

import org.springframework.modulith.ApplicationModule;
