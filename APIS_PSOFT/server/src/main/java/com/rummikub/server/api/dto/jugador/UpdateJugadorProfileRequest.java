package com.rummikub.server.api.dto.jugador;

public class UpdateJugadorProfileRequest {
    private String nombre;
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
