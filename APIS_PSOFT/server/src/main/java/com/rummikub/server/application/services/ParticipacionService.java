package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionId;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ParticipacionRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ParticipacionService {

    private final ParticipacionRepository participacionRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidaRepository partidaRepository;

    public ParticipacionService(ParticipacionRepository participacionRepository, JugadorRepository jugadorRepository, PartidaRepository partidaRepository) {
        this.participacionRepository = participacionRepository;
        this.jugadorRepository = jugadorRepository;
        this.partidaRepository = partidaRepository;
    }

    public List<ParticipacionDTO> getAll() {
        return participacionRepository.findAll().stream().map(Mapper::toDTO).toList();
    }

    public List<ParticipacionDTO> getByJugador(Integer idJugador) {
        return participacionRepository.findByJugador_Id(idJugador).stream().map(Mapper::toDTO).toList();
    }

    public List<ParticipacionDTO> getByPartida(Integer idPartida) {
        return participacionRepository.findByPartida_IdPartida(idPartida).stream().map(Mapper::toDTO).toList();
    }

    public ParticipacionDTO getById(Integer idJugador, Integer idPartida) {
        ParticipacionId id = new ParticipacionId(idJugador, idPartida);
        ParticipacionEntity entity = participacionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Participacion no encontrada"));
        return Mapper.toDTO(entity);
    }

    @Transactional
    public ParticipacionDTO create(ParticipacionDTO dto) {
        if (dto.getIdJugador() == null || dto.getIdPartida() == null) {
            throw new IllegalArgumentException("idJugador e idPartida son obligatorios");
        }
        ParticipacionId id = new ParticipacionId(dto.getIdJugador(), dto.getIdPartida());
        ParticipacionEntity existing = participacionRepository.findById(id).orElse(null);
        if (existing != null) {
            if (!existing.isConectado()) {
                existing.setConectado(true);
                existing = participacionRepository.save(existing);
            }
            return Mapper.toDTO(existing);
        }
        return save(dto);
    }

    public ParticipacionDTO update(Integer idJugador, Integer idPartida, ParticipacionDTO dto) {
        ParticipacionId id = new ParticipacionId(idJugador, idPartida);
        if (!participacionRepository.existsById(id)) {
            throw new NoSuchElementException("Participacion no encontrada");
        }
        dto.setIdJugador(idJugador);
        dto.setIdPartida(idPartida);
        return save(dto);
    }

    public void delete(Integer idJugador, Integer idPartida) {
        ParticipacionId id = new ParticipacionId(idJugador, idPartida);
        if (!participacionRepository.existsById(id)) {
            throw new NoSuchElementException("Participacion no encontrada");
        }
        participacionRepository.deleteById(id);
    }

    public ParticipacionDTO updateConexion(Integer idJugador, Integer idPartida, boolean conectado) {
        ParticipacionId id = new ParticipacionId(idJugador, idPartida);
        ParticipacionEntity entity = participacionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Participacion no encontrada"));
        entity.setConectado(conectado);
        return Mapper.toDTO(participacionRepository.save(entity));
    }

    private ParticipacionDTO save(ParticipacionDTO dto) {
        JugadorEntity jugador = jugadorRepository.findById(dto.getIdJugador())
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + dto.getIdJugador()));
        PartidaEntity partida = partidaRepository.findById(dto.getIdPartida())
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + dto.getIdPartida()));

        ParticipacionId id = new ParticipacionId(dto.getIdJugador(), dto.getIdPartida());
        ParticipacionEntity existing = participacionRepository.findById(id).orElse(null);

        ParticipacionEntity entity = existing == null ? new ParticipacionEntity() : existing;
        entity.setId(id);
        entity.setJugador(jugador);
        entity.setPartida(partida);
        entity.setFichasActuales(dto.getFichasActuales());
        entity.setHabilidadesActuales(dto.getHabilidadesActuales() == null ? "" : dto.getHabilidadesActuales());
        entity.setManoActual(dto.getManoActual() == null ? "" : dto.getManoActual());
        entity.setOrdenTurno(dto.getOrdenTurno());
        if (existing == null) {
            entity.setTurnosInactivo(dto.getTurnosInactivo());
            entity.setConectado(true);
        }
        return Mapper.toDTO(participacionRepository.save(entity));
    }
}
