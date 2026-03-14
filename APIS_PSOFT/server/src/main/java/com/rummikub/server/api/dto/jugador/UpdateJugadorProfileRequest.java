package com.rummikub.server.api.dto.jugador;

import jakarta.validation.constraints.Size;

public class UpdateJugadorProfileRequest {
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @Size(max = 500, message = "La URL de perfil no puede superar 500 caracteres")
    private String perfilURL;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPerfilURL() {
        return perfilURL;
    }

    public void setPerfilURL(String perfilURL) {
        this.perfilURL = perfilURL;
    }
}
