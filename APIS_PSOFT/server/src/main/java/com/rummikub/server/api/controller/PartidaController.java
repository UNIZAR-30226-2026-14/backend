package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.partida.CreatePartidaRequest;
import com.rummikub.server.api.dto.partida.UpdatePartidaRequest;
import com.rummikub.server.application.services.PartidaService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partidas")
@CrossOrigin(origins = "*")
public class PartidaController {

    private final PartidaService partidaService;

    public PartidaController(PartidaService partidaService) {
        this.partidaService = partidaService;
    }

    @GetMapping
    public List<PartidaDTO> getAll() {
        return partidaService.getAll();
    }

    @GetMapping("/{id}")
    public PartidaDTO getById(@PathVariable Integer id) {
        return partidaService.getById(id);
    }

    @PostMapping
    public PartidaDTO create(@Valid @RequestBody CreatePartidaRequest request) {
        PartidaDTO dto = PartidaDTO.builder()
                .idPartida(request.getIdPartida())
                .turno(request.getTurno())
                .fecha(request.getFecha())
                .bolsa(request.getBolsa())
                .mercado(request.getMercado())
                .conjuntoMesa(request.getConjuntoMesa())
                .corriendo(request.isCorriendo())
                .build();
        return partidaService.create(dto);
    }

    @PutMapping("/{id}")
    public PartidaDTO update(@PathVariable Integer id, @Valid @RequestBody UpdatePartidaRequest request) {
        PartidaDTO dto = PartidaDTO.builder()
                .turno(request.getTurno())
                .fecha(request.getFecha())
                .bolsa(request.getBolsa())
                .mercado(request.getMercado())
                .conjuntoMesa(request.getConjuntoMesa())
                .corriendo(request.isCorriendo())
                .build();

        return partidaService.update(id, dto);
    }

    @GetMapping("/{id}/siguiente-turno")
    public PartidaDTO siguienteTurno(@PathVariable Integer id) {
        return partidaService.siguienteTurno(id);
    }

    @PostMapping("/{id}/iniciar")
    public PartidaDTO iniciar(@PathVariable Integer id) {
        return partidaService.iniciar(id);
    }
}
