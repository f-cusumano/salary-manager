package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AziendaSintesi")
public record AziendaSintesiDto(
        long id,
        String ragioneSociale,
        String partitaIva,
        String codiceFiscale,
        String posizioneInps,
        String indirizzo) {
}
