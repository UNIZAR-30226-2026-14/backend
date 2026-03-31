package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParticipacionId implements Serializable {

    @Column(name = "ID_JUGADOR", nullable = false)
    private Integer idJugador;

    @Column(name = "ID_PARTIDA", nullable = false)
    private Integer idPartida;
}
