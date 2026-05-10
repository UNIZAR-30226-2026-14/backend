package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.InvitacionPartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaEntity;
import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaId;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.repository.InvitacionPartidaRepository;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class InvitacionPartidaService {

    private final InvitacionPartidaRepository invitacionPartidaRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidaRepository partidaRepository;

    public InvitacionPartidaService(
            InvitacionPartidaRepository invitacionPartidaRepository,
            JugadorRepository jugadorRepository,
            PartidaRepository partidaRepository) {
        this.invitacionPartidaRepository = invitacionPartidaRepository;
        this.jugadorRepository = jugadorRepository;
        this.partidaRepository = partidaRepository;
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
}
