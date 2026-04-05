package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PlayTurnRequest {

    @NotNull(message = "idJugador es obligatorio")
    private Integer idJugador;

    @NotEmpty(message = "Debes enviar al menos un grupo de fichas")
    private List<@NotEmpty(message = "Cada grupo debe tener fichas") List<String>> grupos;

    public Integer getIdJugador() {
        return idJugador;
    }

    public void setIdJugador(Integer idJugador) {
        this.idJugador = idJugador;
    }

    public List<List<String>> getGrupos() {
        return grupos;
    }

    public void setGrupos(List<List<String>> grupos) {
        this.grupos = grupos;
    }
}
