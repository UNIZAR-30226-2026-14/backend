package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jugador")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class JugadorEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "contrasena", nullable = false)
    private String contrasena;

    @Column(name = "moneda_cosmeticos")
    private int monedaCosmeticos;

    @Column(name = "perfil_url", nullable = false)
    private String perfilURL;

    @Column(name = "partidas_ganadas")
    private int partidasGanadas;

    @Column(name = "partidas_totales")
    private int partidasTotales;

    // si quieres mantener “inventario” como string: "cos1;cos2;cos3"
    @Column(name = "cosmeticos", nullable = false)
    private String cosmeticos;


    public JugadorEntity(String id, String nombre, String contrasena) {
        this.id = id;
        this.nombre = nombre;
        this.contrasena = contrasena;
        this.monedaCosmeticos = 0;
        this.perfilURL = "";
        this.partidasGanadas = 0;
        this.partidasTotales = 0;
        this.cosmeticos = "";
    }
}