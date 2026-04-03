package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.api.dto.jugador.CreateJugadorRequest;
import com.rummikub.server.api.dto.jugador.UpdateJugadorProfileRequest;
import com.rummikub.server.application.services.JugadorService;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jugadores")
public class JugadorController {

    private final JugadorService jugadorService;

    public JugadorController(JugadorService jugadorService) {
        this.jugadorService = jugadorService;
    }

    @GetMapping
    public List<JugadorDTO> getAll() {
        return jugadorService.getAll();
    }

    @GetMapping("/{id}")
    public JugadorDTO getById(@PathVariable Integer id) {
        return jugadorService.getById(id);
    }

    @PostMapping
    public JugadorDTO create(@Valid @RequestBody CreateJugadorRequest request) {
        JugadorEntity jugador = new JugadorEntity();
        jugador.setId(request.getId());
        jugador.setNombre(request.getNombre());
        jugador.setContrasena(request.getContrasena());

        return jugadorService.create(jugador);
    }

    @PatchMapping("/{id}/perfil")
    public JugadorDTO updateProfile(@PathVariable Integer id, @Valid @RequestBody UpdateJugadorProfileRequest request) {
        JugadorEntity jugador = new JugadorEntity();
        jugador.setNombre(request.getNombre());
        jugador.setUrlImgPerfil(request.getUrlImgPerfil());

        return jugadorService.updateProfile(id, jugador);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        jugadorService.delete(id);
    }
}
