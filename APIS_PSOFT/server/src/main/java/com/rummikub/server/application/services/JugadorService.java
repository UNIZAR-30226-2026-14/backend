package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class JugadorService {

    private final JugadorRepository jugadorRepository;

    public JugadorService(JugadorRepository jugadorRepository) {
        this.jugadorRepository = jugadorRepository;
    }

    public List<JugadorDTO> getAll() {
        return jugadorRepository.findAll().stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public JugadorDTO getById(String id) {
        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));
        return Mapper.toDTO(jugador);
    }

    public JugadorDTO create(String id, String nombre, String contrasena) {
        if (jugadorRepository.existsById(id)) {
            throw new IllegalStateException("Ya existe un jugador con id: " + id);
        }

        JugadorEntity jugador = new JugadorEntity(id, nombre, contrasena);
        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public JugadorDTO updateProfile(String id, String nombre, String perfilUrl) {
        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));

        if (nombre != null && !nombre.isBlank()) {
            jugador.setNombre(nombre);
        }
        if (perfilUrl != null) {
            jugador.setPerfilURL(perfilUrl);
        }

        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public void delete(String id) {
        if (!jugadorRepository.existsById(id)) {
            throw new NoSuchElementException("Jugador no encontrado: " + id);
        }
        jugadorRepository.deleteById(id);
    }
}
