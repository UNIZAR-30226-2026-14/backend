package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class TurnActionRequest {

    @NotNull(message = "idJugador es obligatorio")
    private Integer idJugador;

    @Positive(message = "cantidadRobar debe ser mayor que 0")
    private Integer cantidadRobar;

    public Integer getIdJugador() {
        return idJugador;
    }

    public void setIdJugador(Integer idJugador) {
        this.idJugador = idJugador;
    }

    public Integer getCantidadRobar() {
        return cantidadRobar;
    }

    public void setCantidadRobar(Integer cantidadRobar) {
        this.cantidadRobar = cantidadRobar;
    }
}
