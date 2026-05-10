package com.rummikub.server.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitacionPartidaDTO {
    private Integer idEmisor;
    private String nombreEmisor;
    private Integer idInvitado;
    private String nombreInvitado;
    private Integer idPartida;
    private LocalDateTime fechaEnvio;
}
