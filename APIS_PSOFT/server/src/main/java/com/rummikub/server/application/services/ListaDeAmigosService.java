package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ListaDeAmigosRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ListaDeAmigosService {

    private final ListaDeAmigosRepository listaDeAmigosRepository;
    private final JugadorRepository jugadorRepository;

    public ListaDeAmigosService(ListaDeAmigosRepository listaDeAmigosRepository, JugadorRepository jugadorRepository) {
        this.listaDeAmigosRepository = listaDeAmigosRepository;
        this.jugadorRepository = jugadorRepository;
    }

    public List<ListaDeAmigosDTO> getAll() {
        return listaDeAmigosRepository.findAll().stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public ListaDeAmigosDTO getById(String id) {
        ListaDeAmigosEntity relacion = listaDeAmigosRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Relacion de amistad no encontrada: " + id));
        return Mapper.toDTO(relacion);
    }

    public List<ListaDeAmigosDTO> getByJugadorId(String jugadorId) {
        return listaDeAmigosRepository.findByJugador1_IdOrJugador2_Id(jugadorId, jugadorId).stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public ListaDeAmigosDTO create(String jugador1Id, String jugador2Id, String estado, String fecha) {
        if (jugador1Id == null || jugador2Id == null || jugador1Id.isBlank() || jugador2Id.isBlank()) {
            throw new IllegalArgumentException("Los ids de jugador son obligatorios");
        }
        if (jugador1Id.equals(jugador2Id)) {
            throw new IllegalArgumentException("Un jugador no puede agregarse a si mismo");
        }

        JugadorEntity jugador1 = jugadorRepository.findById(jugador1Id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + jugador1Id));
        JugadorEntity jugador2 = jugadorRepository.findById(jugador2Id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + jugador2Id));

        boolean alreadyExists = listaDeAmigosRepository.findByJugador1_IdOrJugador2_Id(jugador1Id, jugador1Id).stream()
                .anyMatch(rel ->
                        (rel.getJugador1().getId().equals(jugador1Id) && rel.getJugador2().getId().equals(jugador2Id))
                                || (rel.getJugador1().getId().equals(jugador2Id) && rel.getJugador2().getId().equals(jugador1Id))
                );

        if (alreadyExists) {
            throw new IllegalStateException("La relacion de amistad ya existe");
        }

        String finalEstado = (estado == null || estado.isBlank()) ? "PENDIENTE" : estado;
        String finalFecha = (fecha == null || fecha.isBlank()) ? LocalDate.now().toString() : fecha;

        ListaDeAmigosEntity nuevaRelacion = new ListaDeAmigosEntity(jugador1, jugador2, finalEstado, finalFecha);
        return Mapper.toDTO(listaDeAmigosRepository.save(nuevaRelacion));
    }

    public ListaDeAmigosDTO updateEstado(String id, String estado) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        ListaDeAmigosEntity relacion = listaDeAmigosRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Relacion de amistad no encontrada: " + id));

        relacion.setEstado(estado);
        return Mapper.toDTO(listaDeAmigosRepository.save(relacion));
    }

    public void delete(String id) {
        if (!listaDeAmigosRepository.existsById(id)) {
            throw new NoSuchElementException("Relacion de amistad no encontrada: " + id);
        }
        listaDeAmigosRepository.deleteById(id);
    }
}
