package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotNull;

public class TurnActionRequest {

    @NotNull(message = "idJugador es obligatorio")
    private Integer idJugador;

    public Integer getIdJugador() {
        return idJugador;
    }

    public void setIdJugador(Integer idJugador) {
        this.idJugador = idJugador;
    }
}
