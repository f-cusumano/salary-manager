package com.fcusumano.sam.calcoli.config;

import com.fcusumano.sam.calcoli.domain.CalcolatoreRiepilogo;
import com.fcusumano.sam.calcoli.domain.ParametriCalcolo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean del modulo calcoli: parametri del dominio dalle properties e calcolatore del riepilogo.
 */
@Configuration(proxyBeanMethods = false)
class CalcoliConfiguration {

    @Bean
    ParametriCalcolo parametriCalcolo(CalcoliProperties calcoli, TfrProperties tfr, FerieProperties ferie) {
        return new ParametriCalcolo(
                ferie.divisoreGiornaliero(),
                ferie.divisoreOrario(),
                ferie.oreGiornaliere(),
                tfr.divisoreQuota(),
                tfr.aliquotaStimata(),
                tfr.impostaSostitutivaRivalutazione(),
                calcoli.tolleranzaAumento(),
                calcoli.mensilitaSenzaCcnl(),
                calcoli.cedoliniBuoniPasto(),
                tfr.tassoRivalutazioneDefault(),
                tfr.mesiMassimiProiezione());
    }

    @Bean
    CalcolatoreRiepilogo calcolatoreRiepilogo(ParametriCalcolo parametri) {
        return new CalcolatoreRiepilogo(parametri);
    }
}
