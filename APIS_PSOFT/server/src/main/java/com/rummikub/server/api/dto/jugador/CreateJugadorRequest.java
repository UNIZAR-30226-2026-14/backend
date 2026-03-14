package com.rummikub.server.api.dto.jugador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateJugadorRequest {
    @NotBlank(message = "El id es obligatorio")
    @Size(max = 64, message = "El id no puede superar 64 caracteres")
    private String id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 6, max = 120, message = "La contrasena debe tener entre 6 y 120 caracteres")
    private String contrasena;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }
}
