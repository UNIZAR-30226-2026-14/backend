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
                .bolsa(normalizeSerializedTiles(part.getBolsa()))
                .mercado(part.getMercado())
                .conjuntoMesa(normalizeSerializedTiles(part.getConjuntoMesa()))
                .eventoActual(part.getEventoActual())
                .modoArcade(part.isModoArcade())
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
                .imagenPerfil(jug.getUrlImgPerfil())
                .partidasGanadas(jug.getPartidasGanadas())
                .partidasPerdidas(jug.getPartidasPerdidas())
                .partidasEmpatadas(jug.getPartidasEmpatadas())
                .partidasPendientes(jug.getPartidasPendientes())
                .partidasFinalizadas(jug.getPartidasFinalizadas())
                .skinFichas(jug.getSkinFichas())
                .skinTablero(jug.getSkinTablero())
                .build();
    }

    public static ListaDeAmigosDTO toDTO(ListaDeAmigosEntity lis) {
        if (lis == null) {
            return null;
        }
        return ListaDeAmigosDTO.builder()
                .jugador1Id(lis.getJugador1().getId())
                .jugador1Nombre(lis.getJugador1().getNombre())
                .jugador2Id(lis.getJugador2().getId())
                .jugador2Nombre(lis.getJugador2().getNombre())
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
                .jugadorNombre(participacion.getJugador().getNombre())
                .jugadorImagenPerfil(participacion.getJugador().getUrlImgPerfil())
                .idPartida(participacion.getPartida().getIdPartida())
                .fichasActuales(participacion.getFichasActuales())
                .habilidadesActuales(participacion.getHabilidadesActuales())
                .manoActual(normalizeSerializedTiles(participacion.getManoActual()))
                .ordenTurno(participacion.getOrdenTurno())
                .turnosInactivo(participacion.getTurnosInactivo())
                .build();
    }

    private static String normalizeSerializedTiles(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }

        String[] groups = encoded.split(";", -1);
        for (int i = 0; i < groups.length; i++) {
            String[] tiles = groups[i].split(",", -1);
            for (int j = 0; j < tiles.length; j++) {
                String token = tiles[j].trim().toUpperCase();
                if ("J1".equals(token) || "J2".equals(token) || "J".equals(token)) {
                    tiles[j] = "J*";
                } else {
                    tiles[j] = token;
                }
            }
            groups[i] = String.join(",", tiles);
        }
        return String.join(";", groups);
    }
}
