package com.rummikub.server.api.dto;

import java.util.List;

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
    private String jugadorImagenPerfil;
    private Integer idPartida;
    private int fichasActuales;
    private int monedasPartida;
    private String habilidadesActuales;
    private List<String> habilidadesCompradas;
    private List<String> efectosActivos;
    private String manoActual;
    private Integer ordenTurno;
    private int turnosInactivo;
    private boolean conectado;
}
