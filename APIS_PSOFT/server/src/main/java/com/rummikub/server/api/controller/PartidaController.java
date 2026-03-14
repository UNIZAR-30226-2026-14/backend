package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.partida.CreatePartidaRequest;
import com.rummikub.server.api.dto.partida.UpdatePartidaRequest;
import com.rummikub.server.application.services.PartidaService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public PartidaDTO getById(@PathVariable String id) {
        return partidaService.getById(id);
    }

    @PostMapping
    public PartidaDTO create(@RequestBody CreatePartidaRequest request) {
        return partidaService.create(request.getId(), request.getTurno());
    }

    @PutMapping("/{id}")
    public PartidaDTO update(@PathVariable String id, @RequestBody UpdatePartidaRequest request) {
        PartidaDTO dto = PartidaDTO.builder()
                .id(id)
                .turno(request.getTurno())
                .bolsa(request.getBolsa())
                .mercado(request.getMercado())
                .fichasJugador1(request.getFichasJugador1())
                .fichasJugador2(request.getFichasJugador2())
                .fichasJugador3(request.getFichasJugador3())
                .fichasJugador4(request.getFichasJugador4())
                .habilidadesJugador1(request.getHabilidadesJugador1())
                .habilidadesJugador2(request.getHabilidadesJugador2())
                .habilidadesJugador3(request.getHabilidadesJugador3())
                .habilidadesJugador4(request.getHabilidadesJugador4())
                .build();

        return partidaService.update(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        partidaService.delete(id);
    }
}
