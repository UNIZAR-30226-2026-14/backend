package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvitacionPartidaId implements Serializable {

    @Column(name = "ID_EMISOR")
    private Integer idEmisor;

    @Column(name = "ID_INVITADO")
    private Integer idInvitado;

    @Column(name = "ID_PARTIDA")
    private Integer idPartida;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvitacionPartidaId that)) return false;
        return Objects.equals(idEmisor, that.idEmisor)
                && Objects.equals(idInvitado, that.idInvitado)
                && Objects.equals(idPartida, that.idPartida);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEmisor, idInvitado, idPartida);
    }
}
