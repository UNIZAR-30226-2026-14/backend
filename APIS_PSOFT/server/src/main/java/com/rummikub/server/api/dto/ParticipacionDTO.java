package com.rummikub.server.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParticipacionDTO {
    private Integer idJugador;
    private String jugadorNombre;
    private String jugadorUrlImgPerfil;
    private Integer idPartida;
    private int fichasActuales;
    private String habilidadesActuales;
    private String manoActual;
    private Integer ordenTurno;
}
