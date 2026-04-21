package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComprarMercadoRequest {

    @NotBlank(message = "codigoObjeto es obligatorio")
    private String codigoObjeto;
}
