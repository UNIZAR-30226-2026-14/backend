package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.InvitacionPartidaDTO;
import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.invitacion.InvitacionesPendientesResponse;
import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaEntity;
import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaId;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.InvitacionPartidaRepository;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ParticipacionRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class InvitacionPartidaService {

    private final InvitacionPartidaRepository invitacionPartidaRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidaRepository partidaRepository;
    private final ParticipacionRepository participacionRepository;

    public InvitacionPartidaService(
            InvitacionPartidaRepository invitacionPartidaRepository,
            JugadorRepository jugadorRepository,
            PartidaRepository partidaRepository,
            ParticipacionRepository participacionRepository) {
        this.invitacionPartidaRepository = invitacionPartidaRepository;
        this.jugadorRepository = jugadorRepository;
        this.partidaRepository = partidaRepository;
        this.participacionRepository = participacionRepository;
    }

    public List<InvitacionPartidaDTO> getAll() {
        return invitacionPartidaRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<InvitacionPartidaDTO> getByInvitadoId(Integer idInvitado) {
        return invitacionPartidaRepository.findByInvitado_Id(idInvitado).stream()
                .map(this::toDTO)
                .toList();
    }

    public InvitacionesPendientesResponse getPendientesConPartidasEnCurso(Integer idInvitado) {
        if (!jugadorRepository.existsById(idInvitado)) {
            throw new NoSuchElementException("Jugador no encontrado: " + idInvitado);
        }

        List<InvitacionPartidaDTO> invitaciones = getByInvitadoId(idInvitado);
        List<PartidaDTO> partidasEnCurso = participacionRepository.findByJugador_Id(idInvitado).stream()
                .map(ParticipacionEntity::getPartida)
                .filter(partida -> partida != null
                        && !"FINISHED".equalsIgnoreCase(safe(partida.getEstado()))
                        && (partida.isCorriendo() || "PAUSED".equalsIgnoreCase(safe(partida.getEstado()))))
                .collect(java.util.stream.Collectors.toMap(
                        PartidaEntity::getIdPartida,
                        partida -> partida,
                        (a, b) -> a,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(this::toPartidaConFichas)
                .toList();

        return InvitacionesPendientesResponse.builder()
                .invitaciones(invitaciones)
                .partidasEnCurso(partidasEnCurso)
                .build();
    }

    public InvitacionPartidaDTO create(Integer idEmisor, Integer idInvitado, Integer idPartida) {
        if (idEmisor == null || idInvitado == null || idPartida == null) {
            throw new IllegalArgumentException("idEmisor, idInvitado e idPartida son obligatorios");
        }
        if (idEmisor.equals(idInvitado)) {
            throw new IllegalArgumentException("No puedes invitarte a ti mismo");
        }

        JugadorEntity emisor = jugadorRepository.findById(idEmisor)
                .orElseThrow(() -> new NoSuchElementException("Jugador emisor no encontrado: " + idEmisor));
        JugadorEntity invitado = jugadorRepository.findById(idInvitado)
                .orElseThrow(() -> new NoSuchElementException("Jugador invitado no encontrado: " + idInvitado));
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

        InvitacionPartidaId id = new InvitacionPartidaId(idEmisor, idInvitado, idPartida);
        if (invitacionPartidaRepository.existsById(id)) {
            throw new IllegalStateException("La invitacion ya existe para este emisor, invitado y partida");
        }

        InvitacionPartidaEntity invitacion = new InvitacionPartidaEntity(
                emisor,
                invitado,
                partida,
                LocalDateTime.now()
        );
        return toDTO(invitacionPartidaRepository.save(invitacion));
    }

    public void delete(Integer idEmisor, Integer idInvitado, Integer idPartida) {
        InvitacionPartidaId id = new InvitacionPartidaId(idEmisor, idInvitado, idPartida);
        if (!invitacionPartidaRepository.existsById(id)) {
            throw new NoSuchElementException("Invitacion no encontrada");
        }
        invitacionPartidaRepository.deleteById(id);
    }

    private InvitacionPartidaDTO toDTO(InvitacionPartidaEntity entity) {
        return InvitacionPartidaDTO.builder()
                .idEmisor(entity.getEmisor().getId())
                .nombreEmisor(entity.getEmisor().getNombre())
                .idInvitado(entity.getInvitado().getId())
                .nombreInvitado(entity.getInvitado().getNombre())
                .idPartida(entity.getPartida().getIdPartida())
                .fechaEnvio(entity.getFechaEnvio())
                .build();
    }

    private PartidaDTO toPartidaConFichas(PartidaEntity partida) {
        PartidaDTO dto = Mapper.toDTO(partida);
        Map<Integer, Integer> fichasPorJugador = new LinkedHashMap<>();
        for (ParticipacionEntity p : participacionRepository.findByPartida_IdPartida(partida.getIdPartida())) {
            if (p.getJugador() != null && p.getJugador().getId() != null) {
                fichasPorJugador.put(p.getJugador().getId(), p.getFichasActuales());
            }
        }
        dto.setFichasPorJugador(fichasPorJugador);
        return dto;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
