package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreatePartidaRequest {
    @NotBlank(message = "El id de partida es obligatorio")
    @Size(max = 64, message = "El id de partida no puede superar 64 caracteres")
    private String id;

    @Min(value = 0, message = "El turno no puede ser negativo")
    private int turno;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getTurno() {
        return turno;
    }

    public void setTurno(int turno) {
        this.turno = turno;
    }
}
