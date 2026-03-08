package com.rummikub.server;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

//CLASEE PARA FUTURO USO EN CAPA SERVICE   
//clase para gestion y edicion de la informacion de fichas jhugador
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class DataJugador {
    //formato del string FichasJugador--> TIPO;IDJUGADOR;PUNTOS;FICHA;FICHA;....
    //TIPO-->"USR" / "BOT"
    //IDJUGADOR->(1-4)para bots / para usuarios su id
    //PUNTOS->entero positivo(de momento)
    //FICHAS siguien formato aun no definido

    private String tipo;
    private String idJugador;
    private List<String> fichas;
    private int puntos;

}
