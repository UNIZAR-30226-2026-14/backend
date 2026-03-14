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

    public JugadorDTO create(JugadorEntity jugador) {
        if (jugador == null || jugador.getId() == null || jugador.getId().isBlank()) {
            throw new IllegalArgumentException("El id del jugador es obligatorio");
        }
        if (jugador.getNombre() == null || jugador.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del jugador es obligatorio");
        }
        if (jugador.getContrasena() == null || jugador.getContrasena().isBlank()) {
            throw new IllegalArgumentException("La contrasena del jugador es obligatoria");
        }
        if (jugadorRepository.existsById(jugador.getId())) {
            throw new IllegalStateException("Ya existe un jugador con id: " + jugador.getId());
        }

        jugador.setMonedaCosmeticos(0);
        if (jugador.getPerfilURL() == null) {
            jugador.setPerfilURL("");
        }
        jugador.setPartidasGanadas(0);
        jugador.setPartidasTotales(0);
        if (jugador.getCosmeticos() == null) {
            jugador.setCosmeticos("");
        }

        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public JugadorDTO updateProfile(String id, JugadorEntity profileData) {
        if (profileData == null) {
            throw new IllegalArgumentException("Los datos de perfil son obligatorios");
        }
        boolean nombreVacio = profileData.getNombre() == null || profileData.getNombre().isBlank();
        boolean urlVacia = profileData.getPerfilURL() == null || profileData.getPerfilURL().isBlank();
        if (nombreVacio && urlVacia) {
            throw new IllegalArgumentException("Debes indicar al menos un campo para actualizar");
        }

        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));

        if (profileData.getNombre() != null && !profileData.getNombre().isBlank()) {
            jugador.setNombre(profileData.getNombre());
        }
        if (profileData.getPerfilURL() != null) {
            jugador.setPerfilURL(profileData.getPerfilURL());
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
