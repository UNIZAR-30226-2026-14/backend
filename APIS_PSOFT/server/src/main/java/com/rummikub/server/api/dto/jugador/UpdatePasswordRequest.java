package com.rummikub.server.api.dto.jugador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePasswordRequest {

    @NotBlank(message = "La contrasena actual es obligatoria")
    @Size(min = 6, max = 120, message = "La contrasena actual debe tener entre 6 y 120 caracteres")
    private String contrasenaActual;

    @NotBlank(message = "La contrasena nueva es obligatoria")
    @Size(min = 6, max = 120, message = "La contrasena nueva debe tener entre 6 y 120 caracteres")
    private String contrasenaNueva;

    public String getContrasenaActual() {
        return contrasenaActual;
    }

    public void setContrasenaActual(String contrasenaActual) {
        this.contrasenaActual = contrasenaActual;
    }

    public String getContrasenaNueva() {
        return contrasenaNueva;
    }

    public void setContrasenaNueva(String contrasenaNueva) {
        this.contrasenaNueva = contrasenaNueva;
    }
}
