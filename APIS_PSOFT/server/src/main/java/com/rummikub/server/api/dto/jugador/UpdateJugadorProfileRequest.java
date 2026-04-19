package com.rummikub.server.api.dto.jugador;

import jakarta.validation.constraints.Size;

public class UpdateJugadorProfileRequest {
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @Size(max = 500, message = "La URL de perfil no puede superar 500 caracteres")
    private String urlImgPerfil;

    @Size(max = 120, message = "skinFichas no puede superar 120 caracteres")
    private String skinFichas;

    @Size(max = 120, message = "skinTablero no puede superar 120 caracteres")
    private String skinTablero;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUrlImgPerfil() {
        return urlImgPerfil;
    }

    public void setUrlImgPerfil(String urlImgPerfil) {
        this.urlImgPerfil = urlImgPerfil;
    }

    public String getSkinFichas() {
        return skinFichas;
    }

    public void setSkinFichas(String skinFichas) {
        this.skinFichas = skinFichas;
    }

    public String getSkinTablero() {
        return skinTablero;
    }

    public void setSkinTablero(String skinTablero) {
        this.skinTablero = skinTablero;
    }
}
