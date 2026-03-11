package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PartidaService {

    private final PartidaRepository partidaRepository;

    public PartidaService(PartidaRepository partidaRepository) {
        this.partidaRepository = partidaRepository;
    }

    public List<PartidaDTO> getAll() {
        return partidaRepository.findAll().stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public PartidaDTO getById(String id) {
        PartidaEntity partida = partidaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + id));
        return Mapper.toDTO(partida);
    }

    public PartidaDTO create(String id, int turno) {
        if (partidaRepository.existsById(id)) {
            throw new IllegalStateException("Ya existe una partida con id: " + id);
        }

        PartidaEntity partida = new PartidaEntity(id, turno);
        return Mapper.toDTO(partidaRepository.save(partida));
    }

    public PartidaDTO update(PartidaDTO dto) {
        if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
            throw new IllegalArgumentException("El id de la partida es obligatorio");
        }

        PartidaEntity partida = partidaRepository.findById(dto.getId())
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + dto.getId()));

        partida.setTurno(dto.getTurno());
        partida.setBolsa(safe(dto.getBolsa()));
        partida.setMercado(safe(dto.getMercado()));
        partida.setFichasJugador1(safe(dto.getFichasJugador1()));
        partida.setFichasJugador2(safe(dto.getFichasJugador2()));
        partida.setFichasJugador3(safe(dto.getFichasJugador3()));
        partida.setFichasJugador4(safe(dto.getFichasJugador4()));
        partida.setHabilidadesJugador1(safe(dto.getHabilidadesJugador1()));
        partida.setHabilidadesJugador2(safe(dto.getHabilidadesJugador2()));
        partida.setHabilidadesJugador3(safe(dto.getHabilidadesJugador3()));
        partida.setHabilidadesJugador4(safe(dto.getHabilidadesJugador4()));

        return Mapper.toDTO(partidaRepository.save(partida));
    }

    public void delete(String id) {
        if (!partidaRepository.existsById(id)) {
            throw new NoSuchElementException("Partida no encontrada: " + id);
        }
        partidaRepository.deleteById(id);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
