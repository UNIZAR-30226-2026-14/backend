package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.ListaDeAmigosDTO;
import com.rummikub.server.api.dto.listaamigos.CreateListaDeAmigosRequest;
import com.rummikub.server.api.dto.listaamigos.UpdateEstadoAmistadRequest;
import com.rummikub.server.application.services.ListaDeAmigosService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/amigos")
@CrossOrigin(origins = "*")
public class ListaDeAmigosController {

    private final ListaDeAmigosService listaDeAmigosService;

    public ListaDeAmigosController(ListaDeAmigosService listaDeAmigosService) {
        this.listaDeAmigosService = listaDeAmigosService;
    }

    @GetMapping
    public List<ListaDeAmigosDTO> getAll(@RequestParam(required = false) Integer jugadorId) {
        if (jugadorId != null) {
            return listaDeAmigosService.getByJugadorId(jugadorId);
        }
        return listaDeAmigosService.getAll();
    }

    @GetMapping("/{jugadorId}/{amigoId}")
    public ListaDeAmigosDTO getById(@PathVariable Integer jugadorId, @PathVariable Integer amigoId) {
        return listaDeAmigosService.getById(jugadorId, amigoId);
    }

    @PostMapping
    public ListaDeAmigosDTO create(@Valid @RequestBody CreateListaDeAmigosRequest request) {
        return listaDeAmigosService.create(
                request.getJugador1Id(),
                request.getJugador2Id(),
                request.getEstado(),
                request.getFecha()
        );
    }

    @PatchMapping("/{jugadorId}/{amigoId}/estado")
    public ListaDeAmigosDTO updateEstado(@PathVariable Integer jugadorId, @PathVariable Integer amigoId, @Valid @RequestBody UpdateEstadoAmistadRequest request) {
        return listaDeAmigosService.updateEstado(jugadorId, amigoId, request.getEstado());
    }

    @DeleteMapping("/{jugadorId}/{amigoId}")
    public ResponseEntity<Void> delete(@PathVariable Integer jugadorId, @PathVariable Integer amigoId) {
        listaDeAmigosService.delete(jugadorId, amigoId);
        return ResponseEntity.noContent().build();
    }
}
