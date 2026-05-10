package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "INVITACION_PARTIDA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvitacionPartidaEntity {

    @EmbeddedId
    private InvitacionPartidaId id;

    @ManyToOne
    @MapsId("idEmisor")
    @JoinColumn(name = "ID_EMISOR", nullable = false)
    private JugadorEntity emisor;

    @ManyToOne
    @MapsId("idInvitado")
    @JoinColumn(name = "ID_INVITADO", nullable = false)
    private JugadorEntity invitado;

    @ManyToOne
    @MapsId("idPartida")
    @JoinColumn(name = "ID_PARTIDA", nullable = false)
    private PartidaEntity partida;

    @Column(name = "FECHA_ENVIO", nullable = false)
    private LocalDateTime fechaEnvio;

    public InvitacionPartidaEntity(JugadorEntity emisor, JugadorEntity invitado, PartidaEntity partida, LocalDateTime fechaEnvio) {
        this.id = new InvitacionPartidaId(emisor.getId(), invitado.getId(), partida.getIdPartida());
        this.emisor = emisor;
        this.invitado = invitado;
        this.partida = partida;
        this.fechaEnvio = fechaEnvio;
    }
}
