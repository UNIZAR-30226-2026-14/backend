package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsarObjetoMercadoRequest {

    @NotBlank(message = "codigoObjeto es obligatorio")
    private String codigoObjeto;

    private Integer idJugadorObjetivo;

    private String codigoObjetoObjetivo;

    private String fichaPropia;

    private String fichaObjetivo;
}
