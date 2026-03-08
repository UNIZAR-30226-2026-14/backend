package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "partida")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class PartidaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;
    @Column(name = "turno", nullable = false)
    private int turno;
    @Column(name = "bolsa", nullable = false)
    private String bolsa;
    @Column(name = "mercado", nullable = false)
    private String mercado;


    @Column(name = "fichasJugador1", nullable = false)
    private String fichasJugador1;
    @Column(name = "fichasJugador2", nullable = false)
    private String fichasJugador2;
    @Column(name = "fichasJugador3", nullable = false)
    private String fichasJugador3;
    @Column(name = "fichasJugador4", nullable = false)
    private String fichasJugador4;


    @Column(name = "habilidadesJugador1", nullable = false)
    private String habilidadesJugador1;
    @Column(name = "habilidadesJugador2", nullable = false)
    private String habilidadesJugador2;
    @Column(name = "habilidadesJugador3", nullable = false)
    private String habilidadesJugador3;
    @Column(name = "habilidadesJugador4", nullable = false)
    private String habilidadesJugador4;


    public PartidaEntity(String id,int turno) {
        this.id = id;

        this.turno=turno;
        this.bolsa="";
        this.mercado="";

        this.fichasJugador1="";
        this.fichasJugador2="";
        this.fichasJugador3="";
        this.fichasJugador4="";

        this.habilidadesJugador1="";
        this.habilidadesJugador2="";
        this.habilidadesJugador3="";
        this.habilidadesJugador4="";
    
    }
}