package com.fcusumano.sam.calcoli.config;

import com.fcusumano.sam.calcoli.domain.CalcolatoreRiepilogo;
import com.fcusumano.sam.calcoli.domain.ParametriCalcolo;
import com.fcusumano.sam.calcoli.domain.ParametriLiquidazione;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean del modulo calcoli: parametri del dominio (riepilogo e liquidazione) dalle properties e calcolatore del
 * riepilogo.
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
    ParametriLiquidazione parametriLiquidazione(LiquidazioneProperties liquidazione) {
        var irpef = liquidazione.irpef() == null ? List.<LiquidazioneProperties.Irpef>of() : liquidazione.irpef();
        return new ParametriLiquidazione(
                liquidazione.aliquotaContributiDefault(),
                irpef.stream()
                        .map(i -> new ParametriLiquidazione.ScaglioniIrpef(i.dalAnno(), i.scaglioni().stream()
                                .map(s -> new ParametriLiquidazione.Scaglione(s.fino(), s.aliquota()))
                                .toList()))
                        .toList());
    }

    @Bean
    CalcolatoreRiepilogo calcolatoreRiepilogo(ParametriCalcolo parametri, ParametriLiquidazione liquidazione) {
        return new CalcolatoreRiepilogo(parametri, liquidazione);
    }
}
