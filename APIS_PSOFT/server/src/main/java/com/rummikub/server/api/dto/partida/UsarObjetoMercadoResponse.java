package com.rummikub.server.api.dto.partida;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UsarObjetoMercadoResponse {
    private Integer idPartida;
    private Integer idJugador;
    private String codigoObjeto;
    private Integer idJugadorObjetivo;
    private boolean consumido;
    private boolean bloqueadoPorGuardianAngel;
    private String mensaje;
    private String manoActual;
    private List<String> habilidadesCompradas;
    private List<String> efectosActivos;
    private List<String> fichasObjetivoVisibles;
    private List<String> habilidadesObjetivoVisibles;
    private List<String> efectosActivosObjetivo;
}
