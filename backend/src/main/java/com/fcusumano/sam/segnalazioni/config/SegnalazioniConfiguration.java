package com.fcusumano.sam.segnalazioni.config;

import com.fcusumano.sam.segnalazioni.domain.RegolaCcnlNonDeterminato;
import com.fcusumano.sam.segnalazioni.domain.RegolaCedolinoInErrore;
import com.fcusumano.sam.segnalazioni.domain.RegolaFineRapportoMancante;
import com.fcusumano.sam.segnalazioni.domain.RegolaFondoTfrInCalo;
import com.fcusumano.sam.segnalazioni.domain.RegolaLivelloInCalo;
import com.fcusumano.sam.segnalazioni.domain.RegolaMensilitaMancante;
import com.fcusumano.sam.segnalazioni.domain.RegolaProgressiviInCalo;
import com.fcusumano.sam.segnalazioni.domain.RegolaQuattordicesimaMancante;
import com.fcusumano.sam.segnalazioni.domain.RegolaScattiInCalo;
import com.fcusumano.sam.segnalazioni.domain.RegolaScattoNonInBusta;
import com.fcusumano.sam.segnalazioni.domain.RegolaTfrIncoerente;
import com.fcusumano.sam.segnalazioni.domain.RegolaTredicesimaMancante;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registrazione delle regole di segnalazione: ogni regola e' un bean {@code RegolaSegnalazione} (pattern strategy).
 * Per aggiungere una regola basta registrarne il bean; il servizio le valuta tutte.
 */
@Configuration(proxyBeanMethods = false)
class SegnalazioniConfiguration {

    @Bean
    RegolaMensilitaMancante regolaMensilitaMancante() {
        return new RegolaMensilitaMancante();
    }

    @Bean
    RegolaTredicesimaMancante regolaTredicesimaMancante(SegnalazioniProperties proprieta) {
        return new RegolaTredicesimaMancante(proprieta.ccnlTredicesimaCassaEdile());
    }

    @Bean
    RegolaQuattordicesimaMancante regolaQuattordicesimaMancante() {
        return new RegolaQuattordicesimaMancante();
    }

    @Bean
    RegolaCedolinoInErrore regolaCedolinoInErrore() {
        return new RegolaCedolinoInErrore();
    }

    @Bean
    RegolaFineRapportoMancante regolaFineRapportoMancante(SegnalazioniProperties proprieta) {
        return new RegolaFineRapportoMancante(proprieta.mesiAttesaFineRapporto());
    }

    @Bean
    RegolaFondoTfrInCalo regolaFondoTfrInCalo(SegnalazioniProperties proprieta) {
        return new RegolaFondoTfrInCalo(proprieta.tolleranzaImporti());
    }

    @Bean
    RegolaTfrIncoerente regolaTfrIncoerente(SegnalazioniProperties proprieta) {
        return new RegolaTfrIncoerente(proprieta.tolleranzaTfr(), proprieta.tolleranzaImporti());
    }

    @Bean
    RegolaScattiInCalo regolaScattiInCalo(SegnalazioniProperties proprieta) {
        return new RegolaScattiInCalo(proprieta.tolleranzaImporti());
    }

    @Bean
    RegolaProgressiviInCalo regolaProgressiviInCalo(SegnalazioniProperties proprieta) {
        return new RegolaProgressiviInCalo(proprieta.tolleranzaImporti());
    }

    @Bean
    RegolaLivelloInCalo regolaLivelloInCalo() {
        return new RegolaLivelloInCalo();
    }

    @Bean
    RegolaScattoNonInBusta regolaScattoNonInBusta() {
        return new RegolaScattoNonInBusta();
    }

    @Bean
    RegolaCcnlNonDeterminato regolaCcnlNonDeterminato() {
        return new RegolaCcnlNonDeterminato();
    }
}
