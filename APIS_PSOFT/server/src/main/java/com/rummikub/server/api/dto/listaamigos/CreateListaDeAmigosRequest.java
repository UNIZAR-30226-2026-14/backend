package com.rummikub.server.api.dto.listaamigos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateListaDeAmigosRequest {
    @NotBlank(message = "El id del jugador solicitante es obligatorio")
    @Size(max = 64, message = "El id del jugador solicitante no puede superar 64 caracteres")
    private String jugador1Id;

    @NotBlank(message = "El id del jugador objetivo es obligatorio")
    @Size(max = 64, message = "El id del jugador objetivo no puede superar 64 caracteres")
    private String jugador2Id;

    @Size(max = 30, message = "El estado no puede superar 30 caracteres")
    private String estado;

    @Size(max = 30, message = "La fecha no puede superar 30 caracteres")
    private String fecha;

    public String getJugador1Id() {
        return jugador1Id;
    }

    public void setJugador1Id(String jugador1Id) {
        this.jugador1Id = jugador1Id;
    }

    public String getJugador2Id() {
        return jugador2Id;
    }

    public void setJugador2Id(String jugador2Id) {
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
