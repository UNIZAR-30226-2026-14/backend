package com.rummikub.server.api.dto.listaamigos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateListaDeAmigosRequest {
    @NotNull(message = "El id del jugador solicitante es obligatorio")
    private Integer jugador1Id;

    @NotNull(message = "El id del jugador objetivo es obligatorio")
    private Integer jugador2Id;

    @Size(max = 30, message = "El estado no puede superar 30 caracteres")
    private String estado;

    @Size(max = 30, message = "La fecha no puede superar 30 caracteres")
    private String fecha;

    public Integer getJugador1Id() {
        return jugador1Id;
    }

    public void setJugador1Id(Integer jugador1Id) {
        this.jugador1Id = jugador1Id;
    }

    public Integer getJugador2Id() {
        return jugador2Id;
    }

    public void setJugador2Id(Integer jugador2Id) {
        this.jugador2Id = jugador2Id;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }
}
