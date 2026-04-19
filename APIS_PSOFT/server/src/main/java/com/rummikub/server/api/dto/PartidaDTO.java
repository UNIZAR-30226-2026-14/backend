package com.rummikub.server.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private LocalDateTime turnoInicio;
    private String estado;
    private Integer ganadorId;
    private String puntuacionFinal;
    private String fichaRobada;
    private boolean corriendo;
}
