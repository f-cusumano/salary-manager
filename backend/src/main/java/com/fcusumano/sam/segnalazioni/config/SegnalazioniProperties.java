package com.fcusumano.sam.segnalazioni.config;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametri delle regole di segnalazione ({@code salary-manager.segnalazioni.*}).
 *
 * @param tolleranzaTfr             scostamento massimo (euro) tra fondo TFR al 31/12 stampato e atteso (TFR_INCOERENTE)
 * @param tolleranzaImporti         calo minimo (euro) considerato per i valori che possono solo crescere (fondo TFR,
 *                                  importo degli scatti, progressivi annui): assorbe arrotondamenti e letture al centesimo
 * @param mesiAttesaFineRapporto    mesi dopo la cessazione entro i quali l'assenza del cedolino di fine rapporto non e'
 *                                  segnalata
 * @param ccnlTredicesimaCassaEdile codici CCNL in cui la tredicesima degli operai e' accantonata presso la Cassa Edile
 *                                  (nessuna segnalazione di tredicesima mancante salvo impiegati e quadri)
 */
@ConfigurationProperties("salary-manager.segnalazioni")
public record SegnalazioniProperties(
        @DefaultValue("5.00") BigDecimal tolleranzaTfr,
        @DefaultValue("1.00") BigDecimal tolleranzaImporti,
        @DefaultValue("2") int mesiAttesaFineRapporto,
        @DefaultValue({"EDILIZIA_INDUSTRIA", "EDILIZIA_ARTIGIANATO"}) Set<String> ccnlTredicesimaCassaEdile) {

    public SegnalazioniProperties {
        if (tolleranzaTfr.signum() < 0 || tolleranzaImporti.signum() < 0 || mesiAttesaFineRapporto < 0) {
            throw new IllegalArgumentException("Parametri delle segnalazioni non validi: tolleranze e mesi non negativi");
        }
        ccnlTredicesimaCassaEdile = Set.copyOf(ccnlTredicesimaCassaEdile);
    }
}
