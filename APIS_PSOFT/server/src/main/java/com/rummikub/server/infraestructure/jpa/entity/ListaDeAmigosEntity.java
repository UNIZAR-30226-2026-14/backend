package com.rummikub.server.infraestructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lista_de_amigos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class ListaDeAmigosEntity {
    
    @Id
    //genera la id de la "relacion" para identificar la entidad lista de amigos
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    //Definimos las relaciones entre jugadores para 
    //la entidad lista de amigos
    //(Dueño, generaará repeticion pero es la manera mas sencilla)
    @ManyToOne
    @JoinColumn(name = "jugador1_id", nullable = false)
    private JugadorEntity jugador1;

    //(Usuario amigo)
    @ManyToOne
    @JoinColumn(name = "jugador2_id", nullable = false)
    private JugadorEntity jugador2;

    @Column(name = "fecha", nullable = false)
    private String fecha;

    @Column(name = "estado", nullable = false)
    private String estado;



    public ListaDeAmigosEntity(JugadorEntity jugador1, JugadorEntity jugador2, String estado,String fecha) {
        this.jugador1 = jugador1;
        this.jugador2 = jugador2;
        this.fecha = fecha;
        this.estado = estado;
    }

}
