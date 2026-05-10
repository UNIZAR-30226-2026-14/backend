package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "PARTIDA")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PartidaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PARTIDA", nullable = false)
    private Integer idPartida;

    @Column(name = "TURNO", nullable = false)
    private int turno;

    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    @Column(name = "MERCADO", length = 5000)
    private String mercado;

    @Column(name = "BOLSA", length = 5000)
    private String bolsa;

    @Column(name = "CONJUNTO_MESA", length = 10000)
    private String conjuntoMesa;

    @Column(name = "EVENTO_ACTUAL", length = 120)
    private String eventoActual;

    @Column(name = "MODO_ARCADE", nullable = false)
    private boolean modoArcade;

    @Column(name = "TURNO_INICIO")
    private LocalDateTime turnoInicio;

    @Column(name = "ESTADO", length = 20)
    private String estado;

    @Column(name = "GANADOR_ID")
    private Integer ganadorId;

    @Column(name = "PUNTUACION_FINAL", length = 2000)
    private String puntuacionFinal;

    @Column(name = "PRIVADA", nullable = false)
    private boolean privada;

    @Column(name = "CORRIENDO", nullable = false)
    private boolean corriendo;

    public PartidaEntity(int turno) {
        this.turno = turno;
        this.fecha = LocalDate.now();
        this.mercado = "";
        this.bolsa = "";
        this.conjuntoMesa = "";
        this.eventoActual = "";
        this.modoArcade = false;
        this.turnoInicio = null;
        this.estado = "WAITING";
        this.ganadorId = null;
        this.puntuacionFinal = "";
        this.privada = false;
        this.corriendo = false;
    }
}
