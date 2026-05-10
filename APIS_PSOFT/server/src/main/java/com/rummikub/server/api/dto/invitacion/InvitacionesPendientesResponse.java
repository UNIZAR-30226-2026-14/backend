package com.rummikub.server.api.dto.invitacion;

import com.rummikub.server.api.dto.InvitacionPartidaDTO;
import com.rummikub.server.api.dto.PartidaDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitacionesPendientesResponse {
    private List<InvitacionPartidaDTO> invitaciones;
    private List<PartidaDTO> partidasEnCurso;
}
