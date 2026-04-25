package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosId;
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

    public ListaDeAmigosDTO getById(Integer jugadorId, Integer amigoId) {
        ListaDeAmigosId id = new ListaDeAmigosId(jugadorId, amigoId);
        ListaDeAmigosEntity relacion = listaDeAmigosRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Relacion de amistad no encontrada"));
        return Mapper.toDTO(relacion);
    }

    public List<ListaDeAmigosDTO> getByJugadorId(Integer jugadorId) {
        return listaDeAmigosRepository.findByJugador1_IdOrJugador2_Id(jugadorId, jugadorId).stream()
                .map(rel -> enrichForJugador(jugadorId, Mapper.toDTO(rel)))
                .toList();
    }

    public ListaDeAmigosDTO create(Integer jugador1Id, Integer jugador2Id, String estado, String fecha) {
        if (jugador1Id == null || jugador2Id == null) {
            throw new IllegalArgumentException("Los ids de jugador son obligatorios");
        }
        if (jugador1Id.equals(jugador2Id)) {
            throw new IllegalArgumentException("Un jugador no puede agregarse a si mismo");
        }

        JugadorEntity jugador1 = jugadorRepository.findById(jugador1Id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + jugador1Id));
        JugadorEntity jugador2 = jugadorRepository.findById(jugador2Id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + jugador2Id));

        ListaDeAmigosId directa = new ListaDeAmigosId(jugador1Id, jugador2Id);
        ListaDeAmigosId inversa = new ListaDeAmigosId(jugador2Id, jugador1Id);
        if (listaDeAmigosRepository.existsById(directa) || listaDeAmigosRepository.existsById(inversa)) {
            throw new IllegalStateException("La relacion de amistad ya existe");
        }

        String finalEstado = (estado == null || estado.isBlank()) ? "PENDIENTE" : estado;
        LocalDate finalFecha;
        if (fecha == null || fecha.isBlank()) {
            finalFecha = LocalDate.now();
        } else {
            try {
                finalFecha = LocalDate.parse(fecha);
            } catch (Exception ex) {
                throw new IllegalArgumentException("La fecha debe tener formato yyyy-MM-dd");
            }
        }

        ListaDeAmigosEntity nuevaRelacion = new ListaDeAmigosEntity(jugador1, jugador2, finalEstado, finalFecha);
        return Mapper.toDTO(listaDeAmigosRepository.save(nuevaRelacion));
    }

    public ListaDeAmigosDTO updateEstado(Integer jugadorId, Integer amigoId, String estado) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        ListaDeAmigosId id = new ListaDeAmigosId(jugadorId, amigoId);
        ListaDeAmigosEntity relacion = listaDeAmigosRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Relacion de amistad no encontrada"));

        relacion.setEstado(estado);
        return Mapper.toDTO(listaDeAmigosRepository.save(relacion));
    }

    public void delete(Integer jugadorId, Integer amigoId) {
        ListaDeAmigosId id = new ListaDeAmigosId(jugadorId, amigoId);
        if (!listaDeAmigosRepository.existsById(id)) {
            throw new NoSuchElementException("Relacion de amistad no encontrada");
        }
        listaDeAmigosRepository.deleteById(id);
    }

    private ListaDeAmigosDTO enrichForJugador(Integer jugadorId, ListaDeAmigosDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getJugador1Id() != null && dto.getJugador1Id().equals(jugadorId)) {
            dto.setAmigoId(dto.getJugador2Id());
            dto.setAmigoNombre(dto.getJugador2Nombre());
        } else if (dto.getJugador2Id() != null && dto.getJugador2Id().equals(jugadorId)) {
            dto.setAmigoId(dto.getJugador1Id());
            dto.setAmigoNombre(dto.getJugador1Nombre());
        }
        return dto;
    }
}
