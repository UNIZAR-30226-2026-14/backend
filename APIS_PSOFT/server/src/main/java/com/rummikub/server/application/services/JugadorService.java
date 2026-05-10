package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ListaDeAmigosRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class JugadorService {

    private final JugadorRepository jugadorRepository;
    private final ListaDeAmigosRepository listaDeAmigosRepository;
    private final PasswordService passwordService;

    public JugadorService(
            JugadorRepository jugadorRepository,
            ListaDeAmigosRepository listaDeAmigosRepository,
            PasswordService passwordService) {
        this.jugadorRepository = jugadorRepository;
        this.listaDeAmigosRepository = listaDeAmigosRepository;
        this.passwordService = passwordService;
    }

    public List<JugadorDTO> getAll() {
        return jugadorRepository.findAll().stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public JugadorDTO getById(Integer id) {
        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));
        return Mapper.toDTO(jugador);
    }

    public JugadorDTO create(JugadorEntity jugador) {
        if (jugador.getNombre() == null || jugador.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del jugador es obligatorio");
        }
        if (jugador.getContrasena() == null || jugador.getContrasena().isBlank()) {
            throw new IllegalArgumentException("La contrasena del jugador es obligatoria");
        }
        if (jugadorRepository.existsByNombreIgnoreCase(jugador.getNombre())) {
            throw new IllegalStateException("Ya existe un jugador con nombre: " + jugador.getNombre());
        }

        jugador.setContrasena(passwordService.hash(jugador.getContrasena()));

        if (jugador.getUrlImgPerfil() == null) {
            jugador.setUrlImgPerfil("");
        }
        if (jugador.getSkinFichas() == null) {
            jugador.setSkinFichas("");
        }
        if (jugador.getSkinTablero() == null) {
            jugador.setSkinTablero("");
        }
        jugador.setMonedas(0);
        jugador.setPartidasGanadas(0);
        jugador.setPartidasPerdidas(0);
        jugador.setPartidasEmpatadas(0);
        jugador.setPartidasPendientes(0);
        jugador.setPartidasFinalizadas(0);

        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public JugadorDTO updateProfile(Integer id, JugadorEntity profileData) {
        if (profileData == null) {
            throw new IllegalArgumentException("Los datos de perfil son obligatorios");
        }
        boolean nombreVacio = profileData.getNombre() == null || profileData.getNombre().isBlank();
        boolean urlVacia = profileData.getUrlImgPerfil() == null || profileData.getUrlImgPerfil().isBlank();
        boolean skinFichasVacia = profileData.getSkinFichas() == null || profileData.getSkinFichas().isBlank();
        boolean skinTableroVacia = profileData.getSkinTablero() == null || profileData.getSkinTablero().isBlank();
        if (nombreVacio && urlVacia && skinFichasVacia && skinTableroVacia) {
            throw new IllegalArgumentException("Debes indicar al menos un campo para actualizar");
        }

        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));

        if (profileData.getNombre() != null && !profileData.getNombre().isBlank()) {
            jugador.setNombre(profileData.getNombre());
        }
        if (profileData.getUrlImgPerfil() != null) {
            jugador.setUrlImgPerfil(profileData.getUrlImgPerfil());
        }
        if (profileData.getSkinFichas() != null) {
            jugador.setSkinFichas(profileData.getSkinFichas());
        }
        if (profileData.getSkinTablero() != null) {
            jugador.setSkinTablero(profileData.getSkinTablero());
        }

        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public JugadorDTO updatePassword(Integer id, String contrasenaActual, String contrasenaNueva) {
        if (contrasenaActual == null || contrasenaActual.isBlank()) {
            throw new IllegalArgumentException("La contrasena actual es obligatoria");
        }
        if (contrasenaNueva == null || contrasenaNueva.isBlank()) {
            throw new IllegalArgumentException("La contrasena nueva es obligatoria");
        }
        if (contrasenaActual.equals(contrasenaNueva)) {
            throw new IllegalArgumentException("La contrasena nueva debe ser distinta de la actual");
        }

        JugadorEntity jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + id));

        if (!passwordService.matches(contrasenaActual, jugador.getContrasena())) {
            throw new SecurityException("La contrasena actual no es correcta");
        }

        jugador.setContrasena(passwordService.hash(contrasenaNueva));
        return Mapper.toDTO(jugadorRepository.save(jugador));
    }

    public void delete(Integer id) {
        if (!jugadorRepository.existsById(id)) {
            throw new NoSuchElementException("Jugador no encontrado: " + id);
        }
        jugadorRepository.deleteById(id);
    }

    public List<JugadorDTO> getFriendProfiles(Integer idJugador, String estado) {
        if (!jugadorRepository.existsById(idJugador)) {
            throw new NoSuchElementException("Jugador no encontrado: " + idJugador);
        }

        List<ListaDeAmigosEntity> relaciones = listaDeAmigosRepository.findByJugador1_IdOrJugador2_Id(idJugador, idJugador);
        String estadoFiltro = estado == null ? null : estado.trim();

        Set<Integer> friendIds = new LinkedHashSet<>();
        for (ListaDeAmigosEntity relacion : relaciones) {
            if (estadoFiltro != null && !estadoFiltro.isBlank()) {
                String estadoActual = relacion.getEstado() == null ? "" : relacion.getEstado().trim();
                if (!estadoActual.equalsIgnoreCase(estadoFiltro)) {
                    continue;
                }
            }

            Integer friendId = relacion.getJugador1().getId().equals(idJugador)
                    ? relacion.getJugador2().getId()
                    : relacion.getJugador1().getId();
            friendIds.add(friendId);
        }

        return jugadorRepository.findAllById(friendIds).stream()
                .map(Mapper::toDTO)
                .toList();
    }
}
