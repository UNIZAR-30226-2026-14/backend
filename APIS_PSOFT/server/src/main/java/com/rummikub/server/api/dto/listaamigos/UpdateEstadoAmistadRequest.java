package com.rummikub.server.api.dto.listaamigos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateEstadoAmistadRequest {
    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 30, message = "El estado no puede superar 30 caracteres")
    private String estado;

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
