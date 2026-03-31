package com.rummikub.server.api.dto.participacion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreateParticipacionRequest {
    @NotNull(message = "idJugador es obligatorio")
    private Integer idJugador;

    @NotNull(message = "idPartida es obligatorio")
    private Integer idPartida;

    @Min(value = 0, message = "fichasActuales no puede ser negativo")
    private int fichasActuales;

    private String habilidadesActuales;

    public Integer getIdJugador() {
        return idJugador;
    }

    public void setIdJugador(Integer idJugador) {
        this.idJugador = idJugador;
    }

    public Integer getIdPartida() {
        return idPartida;
    }

    public void setIdPartida(Integer idPartida) {
        this.idPartida = idPartida;
    }

    public int getFichasActuales() {
        return fichasActuales;
    }

    public void setFichasActuales(int fichasActuales) {
        this.fichasActuales = fichasActuales;
    }

    public String getHabilidadesActuales() {
        return habilidadesActuales;
    }

    public void setHabilidadesActuales(String habilidadesActuales) {
        this.habilidadesActuales = habilidadesActuales;
    }
}
