package com.rummikub.server.api.dto.partida;

import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.api.dto.PartidaDTO;
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
public class MatchmakingResponse {

    private boolean creadaNuevaPartida;
    private PartidaDTO partida;
    private ParticipacionDTO participacion;
}
