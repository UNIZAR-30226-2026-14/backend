package com.rummikub.server.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.Map;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PartidaDTO {

    private Integer idPartida;
    private int turno;
    private LocalDate fecha;
    private String mercado;
    private String bolsa;
    private String conjuntoMesa;
    private String eventoActual;
    private Boolean modoArcade;
    private LocalDateTime turnoInicio;
    private String estado;
    private Integer ganadorId;
    private Map<String, Object> puntuacionFinal;
    private Boolean privada;
    private String fichaRobada;
    private List<String> fichasRobadas;
    private Map<Integer, Integer> fichasPorJugador;
    private boolean corriendo;
}
