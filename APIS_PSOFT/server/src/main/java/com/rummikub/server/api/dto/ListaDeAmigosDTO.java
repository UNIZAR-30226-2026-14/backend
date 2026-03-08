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
public class ListaDeAmigosDTO {

    private String id;
    //solo los id´s que es lo que nos interesa
    private String jugador1;
    private String jugador2;
    private String fecha;
    private String estado;

}
