package com.rummikub.server.infraestructure.jpa.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Mapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                .puntuacionFinal(parseScoreSummary(part.getPuntuacionFinal()))
                .privada(part.isPrivada())
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
                .habilidadesCompradas(parsePurchasedCodes(participacion.getHabilidadesActuales()))
                .efectosActivos(parseActiveEffects(participacion.getHabilidadesActuales()))
                .manoActual(normalizeSerializedTiles(participacion.getManoActual()))
                .ordenTurno(participacion.getOrdenTurno())
                .turnosInactivo(participacion.getTurnosInactivo())
                .conectado(participacion.isConectado())
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

    private static Map<String, Object> parseScoreSummary(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(rawValue, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", rawValue);
        }
    }

    private static List<String> parsePurchasedCodes(String rawValue) {
        List<String> result = new ArrayList<>();
        if (rawValue == null || rawValue.isBlank()) {
            return result;
        }
        for (String token : rawValue.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.isEmpty() || normalized.startsWith("FX:")) {
                continue;
            }
            result.add(normalized);
        }
        return result;
    }

    private static List<String> parseActiveEffects(String rawValue) {
        List<String> result = new ArrayList<>();
        if (rawValue == null || rawValue.isBlank()) {
            return result;
        }
        for (String token : rawValue.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.startsWith("FX:") && normalized.length() > 3) {
                result.add(normalized.substring(3));
            }
        }
        return result;
    }
}
