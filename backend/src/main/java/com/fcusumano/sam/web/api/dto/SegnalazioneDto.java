package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Segnalazione")
public record SegnalazioneDto(
        long id,
        TipoSegnalazione tipo,
        Gravita gravita,
        String titolo,
        String descrizione,
        Long cedolinoId,
        CompetenzaDto competenza,
        List<EvidenzaDto> evidenze) {
}
