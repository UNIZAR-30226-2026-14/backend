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
public class ListaDeAmigosDTO {
    private Integer jugador1;
    private String jugador1Nombre;
    private Integer jugador2;
    private String jugador2Nombre;
    private Integer amigoId;
    private String amigoNombre;
    private String fecha;
    private String estado;
}
