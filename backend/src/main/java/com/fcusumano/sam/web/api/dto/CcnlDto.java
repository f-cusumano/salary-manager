package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Ccnl")
public record CcnlDto(
        String codice,
        String descrizione,
        int mensilita,
        Integer periodicitaScattiMesi,
        Integer maxScatti,
        Integer meseQuattordicesima,
        String noteVerifica) {
}
