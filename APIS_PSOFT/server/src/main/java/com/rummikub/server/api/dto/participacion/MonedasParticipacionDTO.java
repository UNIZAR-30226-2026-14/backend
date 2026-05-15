package com.rummikub.server.api.dto.participacion;

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
public class MonedasParticipacionDTO {
    private Integer idJugador;
    private Integer idPartida;
    private int monedasPartida;
}
