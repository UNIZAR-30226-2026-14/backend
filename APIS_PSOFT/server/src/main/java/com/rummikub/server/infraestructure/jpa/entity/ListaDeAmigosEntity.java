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

@Entity
@Table(name = "LISTA_AMIGOS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListaDeAmigosEntity {

    @EmbeddedId
    private ListaDeAmigosId id;

    @ManyToOne
    @MapsId("idJugador")
    @JoinColumn(name = "ID_JUGADOR", nullable = false)
    private JugadorEntity jugador1;

    @ManyToOne
    @MapsId("idAmigo")
    @JoinColumn(name = "ID_AMIGO", nullable = false)
    private JugadorEntity jugador2;

    @Column(name = "FECHA", nullable = false)
    private String fecha;

    @Column(name = "ESTADO", nullable = false)
    private String estado;

    public ListaDeAmigosEntity(JugadorEntity jugador1, JugadorEntity jugador2, String estado, String fecha) {
        this.id = new ListaDeAmigosId(jugador1.getId(), jugador2.getId());
        this.jugador1 = jugador1;
        this.jugador2 = jugador2;
        this.fecha = fecha;
        this.estado = estado;
    }
}
