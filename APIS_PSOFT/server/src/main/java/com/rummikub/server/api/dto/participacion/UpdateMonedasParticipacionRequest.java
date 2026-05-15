package com.rummikub.server.api.dto.participacion;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMonedasParticipacionRequest {

    @Min(value = 0, message = "monedasPartida no puede ser negativo")
    private int monedasPartida;
}
