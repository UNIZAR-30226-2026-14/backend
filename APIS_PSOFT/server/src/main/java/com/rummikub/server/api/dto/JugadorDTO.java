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
public class JugadorDTO {
    private Integer id;
    private String nombre;
    private int monedas;
    private String urlImgPerfil;
    private int partidasGanadas;
    private int partidasPerdidas;
    private int partidasEmpatadas;
    private int partidasPendientes;
    private int partidasFinalizadas;
    private String skinFichas;
    private String skinTablero;
}
