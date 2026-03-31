package com.rummikub.server.api.dto.participacion;

import jakarta.validation.constraints.Min;

public class UpdateParticipacionRequest {
    @Min(value = 0, message = "fichasActuales no puede ser negativo")
    private int fichasActuales;

    private String habilidadesActuales;

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
