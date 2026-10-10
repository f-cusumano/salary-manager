/**
 * Modulo CCNL: tabella di lookup dei contratti collettivi (mensilita', scatti, mese della quattordicesima)
 * e riconoscimento del CCNL dal testo stampato sul cedolino.
 * <p>
 * API pubblica: {@link com.fcusumano.sam.ccnl.CatalogoCcnl}, {@link com.fcusumano.sam.ccnl.Ccnl}.
 * Tabelle proprie: {@code ccnl}.
 */
@ApplicationModule(displayName = "CCNL", allowedDependencies = "shared")
package com.fcusumano.sam.ccnl;

import org.springframework.modulith.ApplicationModule;
