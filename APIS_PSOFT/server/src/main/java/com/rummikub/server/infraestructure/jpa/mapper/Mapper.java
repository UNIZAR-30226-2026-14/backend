package com.rummikub.server.infraestructure.jpa.mapper;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;

//CLASE ENCARGADA DE TRASPASO DE ENTITY A DTO
//Se usa para gestion interna de la informacion
//y adaptacion a los formatos de salida

public class Mapper {
    
    //Partida
    public static PartidaDTO toDTO(PartidaEntity part){
        if(part != null){
            return PartidaDTO.builder()
                .id(part.getId())
                .turno(part.getTurno())
                .bolsa(part.getBolsa())
                .mercado(part.getMercado())
                .fichasJugador1(part.getFichasJugador1())
                .fichasJugador2(part.getFichasJugador2())
                .fichasJugador3(part.getFichasJugador3())
                .fichasJugador4(part.getFichasJugador4())
                .habilidadesJugador1(part.getHabilidadesJugador1())
                .habilidadesJugador2(part.getHabilidadesJugador2())
                .habilidadesJugador3(part.getHabilidadesJugador3())
                .habilidadesJugador4(part.getHabilidadesJugador4())
                .build();

        }else{return null;}
    }


    //Jugador
    public static JugadorDTO toDTO(JugadorEntity jug){
        if(jug != null){
            return JugadorDTO.builder() 
                .id(jug.getId())
                .nombre(jug.getNombre())
                .monedaCosmeticos(jug.getMonedaCosmeticos())
                .perfilURL(jug.getPerfilURL())
                .partidasGanadas(jug.getPartidasGanadas())
                .partidasTotales(jug.getPartidasTotales())
                .cosmeticos(jug.getCosmeticos())
                .build();

        }else{return null;}
    }


    //Lista de amigos
    public static ListaDeAmigosDTO toDTO(ListaDeAmigosEntity lis){
        if(lis != null){
            return ListaDeAmigosDTO.builder()   
                .id(lis.getId())
                .jugador1(lis.getJugador1().getId())
                .jugador2(lis.getJugador2().getId())
                .fecha(lis.getFecha())
                .estado(lis.getEstado())
                .build();

        }else{return null;}
    }


}
