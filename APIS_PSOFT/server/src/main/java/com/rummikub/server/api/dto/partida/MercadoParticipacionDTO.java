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
public class MercadoParticipacionDTO {
    private Integer idPartida;
    private Integer idJugador;
    private int monedasJugador;
    private List<MercadoItemDTO> objetosMercado;
    private List<String> habilidadesCompradas;
}
