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
@Builder//para el mapper, así lo crea solo
public class PartidaDTO {

    private String id;
    private int turno;
    private String bolsa;
    private String mercado;
    private String fichasJugador1;
    private String fichasJugador2;
    private String fichasJugador3;
    private String fichasJugador4;
    private String habilidadesJugador1;
    private String habilidadesJugador2;
    private String habilidadesJugador3;
    private String habilidadesJugador4;
}
