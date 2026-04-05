package com.rummikub.server.infraestructure.jpa.mapper;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;

public class Mapper {

    public static PartidaDTO toDTO(PartidaEntity part) {
        if (part == null) {
            return null;
        }
        return PartidaDTO.builder()
                .idPartida(part.getIdPartida())
                .turno(part.getTurno())
                .fecha(part.getFecha())
                .bolsa(part.getBolsa())
                .mercado(part.getMercado())
                .conjuntoMesa(part.getConjuntoMesa())
                .turnoInicio(part.getTurnoInicio())
                .estado(part.getEstado())
                .ganadorId(part.getGanadorId())
                .puntuacionFinal(part.getPuntuacionFinal())
                .corriendo(part.isCorriendo())
                .build();
    }

    public static JugadorDTO toDTO(JugadorEntity jug) {
        if (jug == null) {
            return null;
        }
        return JugadorDTO.builder()
                .id(jug.getId())
                .nombre(jug.getNombre())
                .monedas(jug.getMonedas())
                .urlImgPerfil(jug.getUrlImgPerfil())
                .partidasGanadas(jug.getPartidasGanadas())
                .partidasPerdidas(jug.getPartidasPerdidas())
                .partidasEmpatadas(jug.getPartidasEmpatadas())
                .partidasPendientes(jug.getPartidasPendientes())
                .partidasFinalizadas(jug.getPartidasFinalizadas())
                .cosmeticos(jug.getCosmeticos())
                .build();
    }

    public static ListaDeAmigosDTO toDTO(ListaDeAmigosEntity lis) {
        if (lis == null) {
            return null;
        }
        return ListaDeAmigosDTO.builder()
                .jugador1(lis.getJugador1().getId())
                .jugador2(lis.getJugador2().getId())
                .fecha(lis.getFecha() != null ? lis.getFecha().toString() : null)
                .estado(lis.getEstado())
                .build();
    }

    public static ParticipacionDTO toDTO(ParticipacionEntity participacion) {
        if (participacion == null) {
            return null;
        }
        return ParticipacionDTO.builder()
                .idJugador(participacion.getJugador().getId())
                .idPartida(participacion.getPartida().getIdPartida())
                .fichasActuales(participacion.getFichasActuales())
                .habilidadesActuales(participacion.getHabilidadesActuales())
                .manoActual(participacion.getManoActual())
                .ordenTurno(participacion.getOrdenTurno())
                .build();
    }
}
