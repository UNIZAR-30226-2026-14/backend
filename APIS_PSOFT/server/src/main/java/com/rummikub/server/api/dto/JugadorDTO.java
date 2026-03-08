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
public class JugadorDTO {
    //se usara para entradas y salidas de la api, en este caso quitamos la
    //info sensible, contraseña
    private String id;
    private String nombre;
    private int monedaCosmeticos;
    private String perfilURL;
    private int partidasGanadas;
    private int partidasTotales;
    private String cosmeticos;
}
