package com.rummikub.server.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class JugadorDTO {
    private Integer id;
    private String nombre;
    private int monedas;
    @JsonProperty("imagenPerfil")
    @JsonAlias("urlImgPerfil")
    private String imagenPerfil;
    private int partidasGanadas;
    private int partidasPerdidas;
    private int partidasEmpatadas;
    private int partidasPendientes;
    private int partidasFinalizadas;
    private String skinFichas;
    private String skinTablero;
}
