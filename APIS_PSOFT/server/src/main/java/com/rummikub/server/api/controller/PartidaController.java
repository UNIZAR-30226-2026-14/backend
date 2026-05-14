package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.partida.ComprarMercadoRequest;
import com.rummikub.server.api.dto.partida.CreatePartidaRequest;
import com.rummikub.server.api.dto.partida.MatchmakingRequest;
import com.rummikub.server.api.dto.partida.MatchmakingResponse;
import com.rummikub.server.api.dto.partida.MercadoParticipacionDTO;
import com.rummikub.server.api.dto.partida.PlayAdvancedTurnRequest;
import com.rummikub.server.api.dto.partida.PlayTurnRequest;
import com.rummikub.server.api.dto.partida.TurnActionRequest;
import com.rummikub.server.api.dto.partida.UsarObjetoMercadoRequest;
import com.rummikub.server.api.dto.partida.UsarObjetoMercadoResponse;
import com.rummikub.server.api.dto.partida.UpdatePartidaRequest;
import com.rummikub.server.application.services.AuthService;
import com.rummikub.server.application.services.PartidaService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partidas")
@CrossOrigin(origins = "*")
public class PartidaController {

    private final PartidaService partidaService;
    private final AuthService authService;

    public PartidaController(PartidaService partidaService, AuthService authService) {
        this.partidaService = partidaService;
        this.authService = authService;
    }

    @GetMapping
    public List<PartidaDTO> getAll(
            @RequestParam(required = false) Integer usuarioId,
            @RequestParam(required = false) Boolean modoArcade,
            @RequestParam(required = false) Boolean privada,
            @RequestParam(required = false) String estado) {
        if (usuarioId != null) {
            return partidaService.getByUsuario(usuarioId);
        }
        if (Boolean.FALSE.equals(privada) && "WAITING".equalsIgnoreCase(estado)) {
            return partidaService.getOpenPublicGames(modoArcade);
        }
        return partidaService.getAll();
    }

    @GetMapping("/{id}")
    public PartidaDTO getById(@PathVariable Integer id) {
        return partidaService.getById(id);
    }

    @PostMapping
    public PartidaDTO create(@Valid @RequestBody CreatePartidaRequest request) {
        PartidaDTO dto = PartidaDTO.builder()
                .turno(request.getTurno())
                .fecha(request.getFecha())
                .bolsa(request.getBolsa())
                .mercado(request.getMercado())
                .conjuntoMesa(request.getConjuntoMesa())
                .modoArcade(Boolean.TRUE.equals(request.getModoArcade()))
                .privada(Boolean.TRUE.equals(request.getPrivada()))
                .corriendo(request.isCorriendo())
                .build();
        return partidaService.create(dto);
    }

    @PostMapping("/matchmaking")
    public MatchmakingResponse matchmaking(
            @RequestBody(required = false) MatchmakingRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        boolean modoArcade = request != null && Boolean.TRUE.equals(request.getModoArcade());
        return partidaService.matchmaking(idJugador, modoArcade);
    }

    @PutMapping("/{id}")
    public PartidaDTO update(@PathVariable Integer id, @Valid @RequestBody UpdatePartidaRequest request) {
        PartidaDTO dto = PartidaDTO.builder()
                .turno(request.getTurno())
                .fecha(request.getFecha())
                .bolsa(request.getBolsa())
                .mercado(request.getMercado())
                .conjuntoMesa(request.getConjuntoMesa())
                .modoArcade(request.getModoArcade())
                .privada(request.getPrivada())
                .corriendo(request.isCorriendo())
                .build();

        return partidaService.update(id, dto);
    }

    @GetMapping("/{id}/turno-actual")
    public PartidaDTO turnoActual(@PathVariable Integer id) {
        return partidaService.getById(id);
    }

    @PostMapping("/{id}/siguiente-turno")
    public PartidaDTO siguienteTurno(
            @PathVariable Integer id,
            @Valid @RequestBody TurnActionRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.siguienteTurno(id, request.getIdJugador());
    }

    @PostMapping("/{id}/pasar")
    public PartidaDTO pasar(
            @PathVariable Integer id,
            @Valid @RequestBody TurnActionRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.pasarTurno(id, request.getIdJugador());
    }

    @PostMapping("/{id}/robar")
    public PartidaDTO robar(
            @PathVariable Integer id,
            @Valid @RequestBody TurnActionRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.robarFicha(id, request.getIdJugador());
    }

    @PostMapping("/{id}/solo-robar")
    public PartidaDTO soloRobar(
            @PathVariable Integer id,
            @Valid @RequestBody TurnActionRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.robarSinPasarTurno(id, request.getIdJugador(), request.getCantidadRobar());
    }

    @PostMapping("/{id}/jugar")
    public PartidaDTO jugar(
            @PathVariable Integer id,
            @Valid @RequestBody PlayTurnRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.jugarGrupos(id, request.getIdJugador(), request.getGrupos());
    }

    @PostMapping("/{id}/jugar-avanzado")
    public PartidaDTO jugarAvanzado(
            @PathVariable Integer id,
            @Valid @RequestBody PlayAdvancedTurnRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.jugarAvanzado(
                id,
                request.getIdJugador(),
                request.getMoveType(),
                request.getGrupos(),
                request.getExtendIndex(),
                request.getExtensionTiles(),
                request.getNewBoard()
        );
    }

    @GetMapping("/{id}/mercado")
    public MercadoParticipacionDTO getMercadoJugador(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.getMercadoJugador(id, idJugador);
    }

    @PostMapping("/{id}/mercado/comprar")
    public MercadoParticipacionDTO comprarObjetoMercado(
            @PathVariable Integer id,
            @Valid @RequestBody ComprarMercadoRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.comprarObjetoMercado(id, idJugador, request.getCodigoObjeto());
    }

    @PostMapping("/{id}/mercado/usar")
    public UsarObjetoMercadoResponse usarObjetoMercado(
            @PathVariable Integer id,
            @Valid @RequestBody UsarObjetoMercadoRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.usarObjetoMercado(
                id,
                idJugador,
                request.getCodigoObjeto(),
                request.getIdJugadorObjetivo(),
                request.getCodigoObjetoObjetivo(),
                request.getFichaPropia(),
                request.getFichaObjetivo()
        );
    }

    @PostMapping("/{id}/iniciar")
    public PartidaDTO iniciar(@PathVariable Integer id) {
        return partidaService.iniciar(id);
    }

    @PostMapping("/{id}/pausar")
    public PartidaDTO pausar(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.pausarPartida(id, idJugador);
    }

    @PostMapping("/{id}/reanudar")
    public PartidaDTO reanudar(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.reanudarPartida(id, idJugador);
    }

    @PostMapping("/{id}/salir")
    public PartidaDTO salir(
            @PathVariable Integer id,
            @Valid @RequestBody TurnActionRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, request.getIdJugador());
        return partidaService.salirPartida(id, request.getIdJugador());
    }

    @PostMapping("/{id}/finalizar")
    public PartidaDTO finalizar(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idJugador = authService.requireUserId(authorizationHeader);
        return partidaService.finalizarPartida(id, idJugador);
    }
}
