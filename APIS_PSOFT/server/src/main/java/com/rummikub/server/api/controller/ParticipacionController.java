package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.api.dto.participacion.CreateParticipacionRequest;
import com.rummikub.server.api.dto.participacion.MonedasParticipacionDTO;
import com.rummikub.server.api.dto.participacion.UpdateConexionParticipacionRequest;
import com.rummikub.server.api.dto.participacion.UpdateMonedasParticipacionRequest;
import com.rummikub.server.api.dto.participacion.UpdateParticipacionRequest;
import com.rummikub.server.application.services.ParticipacionService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/participaciones")
@CrossOrigin(origins = "*")
public class ParticipacionController {

    private final ParticipacionService participacionService;

    public ParticipacionController(ParticipacionService participacionService) {
        this.participacionService = participacionService;
    }

    @GetMapping
    public List<ParticipacionDTO> getAll(
            @RequestParam(required = false) Integer jugadorId,
            @RequestParam(required = false) Integer partidaId) {
        if (jugadorId != null) {
            return participacionService.getByJugador(jugadorId);
        }
        if (partidaId != null) {
            return participacionService.getByPartida(partidaId);
        }
        return participacionService.getAll();
    }

    @GetMapping("/{idJugador}/{idPartida}")
    public ParticipacionDTO getById(@PathVariable Integer idJugador, @PathVariable Integer idPartida) {
        return participacionService.getById(idJugador, idPartida);
    }

    @GetMapping("/{idJugador}/{idPartida}/monedas")
    public MonedasParticipacionDTO mirarMonedas(
            @PathVariable Integer idJugador,
            @PathVariable Integer idPartida) {
        return participacionService.getMonedas(idJugador, idPartida);
    }

    @PostMapping
    public ParticipacionDTO create(@Valid @RequestBody CreateParticipacionRequest request) {
        ParticipacionDTO dto = ParticipacionDTO.builder()
                .idJugador(request.getIdJugador())
                .idPartida(request.getIdPartida())
                .fichasActuales(request.getFichasActuales())
                .habilidadesActuales(request.getHabilidadesActuales())
                .build();
        return participacionService.create(dto);
    }

    @PutMapping("/{idJugador}/{idPartida}")
    public ParticipacionDTO update(
            @PathVariable Integer idJugador,
            @PathVariable Integer idPartida,
            @Valid @RequestBody UpdateParticipacionRequest request) {
        ParticipacionDTO dto = ParticipacionDTO.builder()
                .fichasActuales(request.getFichasActuales())
                .habilidadesActuales(request.getHabilidadesActuales())
                .build();
        return participacionService.update(idJugador, idPartida, dto);
    }

    @PatchMapping("/{idJugador}/{idPartida}/conexion")
    public ParticipacionDTO updateConexion(
            @PathVariable Integer idJugador,
            @PathVariable Integer idPartida,
            @Valid @RequestBody UpdateConexionParticipacionRequest request) {
        return participacionService.updateConexion(idJugador, idPartida, request.getConectado());
    }

    @PatchMapping("/{idJugador}/{idPartida}/monedas")
    public MonedasParticipacionDTO modificarMonedas(
            @PathVariable Integer idJugador,
            @PathVariable Integer idPartida,
            @Valid @RequestBody UpdateMonedasParticipacionRequest request) {
        return participacionService.updateMonedas(idJugador, idPartida, request.getMonedasPartida());
    }
}
