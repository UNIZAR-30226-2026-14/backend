package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "PARTIDA")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PartidaEntity {

    @Id
    @Column(name = "ID_PARTIDA", nullable = false)
    private Integer idPartida;

    @Column(name = "TURNO", nullable = false)
    private int turno;

    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    @Column(name = "MERCADO")
    private String mercado;

    @Column(name = "BOLSA")
    private String bolsa;

    @Column(name = "CONJUNTO_MESA")
    private String conjuntoMesa;

    @Column(name = "CORRIENDO", nullable = false)
    private boolean corriendo;

    public PartidaEntity(Integer idPartida, int turno) {
        this.idPartida = idPartida;
        this.turno = turno;
        this.fecha = LocalDate.now();
        this.mercado = "";
        this.bolsa = "";
        this.conjuntoMesa = "";
        this.corriendo = false;
    }
}
