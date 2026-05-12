package com.rummikub.server.api.dto.participacion;

import jakarta.validation.constraints.NotNull;

public class UpdateConexionParticipacionRequest {

    @NotNull(message = "conectado es obligatorio")
    private Boolean conectado;

    public Boolean getConectado() {
        return conectado;
    }

    public void setConectado(Boolean conectado) {
        this.conectado = conectado;
    }
}
