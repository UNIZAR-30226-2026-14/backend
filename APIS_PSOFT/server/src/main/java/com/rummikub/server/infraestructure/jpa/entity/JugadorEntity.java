package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "JUGADOR")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JugadorEntity {

    @Id
    @Column(name = "ID", nullable = false)
    private Integer id;

    @Column(name = "NOMBRE", nullable = false)
    private String nombre;

    @Column(name = "CONTRASENA", nullable = false)
    private String contrasena;

    @Column(name = "URL_IMG_PERFIL")
    private String urlImgPerfil;

    @Column(name = "COSMETICOS")
    private String cosmeticos;

    @Column(name = "MONEDAS", nullable = false)
    private int monedas;

    @Column(name = "PARTIDAS_GANADAS", nullable = false)
    private int partidasGanadas;

    @Column(name = "PARTIDAS_PERDIDAS", nullable = false)
    private int partidasPerdidas;

    @Column(name = "PARTIDAS_EMPATADAS", nullable = false)
    private int partidasEmpatadas;

    @Column(name = "PARTIDAS_PENDIENTES", nullable = false)
    private int partidasPendientes;

    @Column(name = "PARTIDAS_FINALIZADAS", nullable = false)
    private int partidasFinalizadas;

    public JugadorEntity(Integer id, String nombre, String contrasena) {
        this.id = id;
        this.nombre = nombre;
        this.contrasena = contrasena;
        this.urlImgPerfil = "";
        this.cosmeticos = "";
        this.monedas = 0;
        this.partidasGanadas = 0;
        this.partidasPerdidas = 0;
        this.partidasEmpatadas = 0;
        this.partidasPendientes = 0;
        this.partidasFinalizadas = 0;
    }
}
