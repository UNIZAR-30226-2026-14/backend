package com.rummikub.server.api.dto.invitacion;

import jakarta.validation.constraints.NotNull;

public class CreateInvitacionPartidaRequest {

    @NotNull(message = "idInvitado es obligatorio")
    private Integer idInvitado;

    @NotNull(message = "idPartida es obligatorio")
    private Integer idPartida;

    public Integer getIdInvitado() {
        return idInvitado;
    }

    public void setIdInvitado(Integer idInvitado) {
        this.idInvitado = idInvitado;
    }

    public Integer getIdPartida() {
        return idPartida;
    }

    public void setIdPartida(Integer idPartida) {
        this.idPartida = idPartida;
    }
}
