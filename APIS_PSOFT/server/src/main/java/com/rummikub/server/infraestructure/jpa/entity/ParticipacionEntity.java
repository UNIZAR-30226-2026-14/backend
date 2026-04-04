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
@Table(name = "PARTICIPACION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParticipacionEntity {

    @EmbeddedId
    private ParticipacionId id;

    @ManyToOne
    @MapsId("idJugador")
    @JoinColumn(name = "ID_JUGADOR", nullable = false)
    private JugadorEntity jugador;

    @ManyToOne
    @MapsId("idPartida")
    @JoinColumn(name = "ID_PARTIDA", nullable = false)
    private PartidaEntity partida;

    @Column(name = "FICHAS_ACTUALES", nullable = false)
    private int fichasActuales;

    @Column(name = "HABILIDADES_ACTUALES", length = 1000)
    private String habilidadesActuales;

    @Column(name = "MANO_ACTUAL", length = 5000)
    private String manoActual;

    @Column(name = "ORDEN_TURNO")
    private Integer ordenTurno;

    public ParticipacionEntity(JugadorEntity jugador, PartidaEntity partida, int fichasActuales, String habilidadesActuales) {
        this.id = new ParticipacionId(jugador.getId(), partida.getIdPartida());
        this.jugador = jugador;
        this.partida = partida;
        this.fichasActuales = fichasActuales;
        this.habilidadesActuales = habilidadesActuales;
        this.manoActual = "";
        this.ordenTurno = null;
    }
}
