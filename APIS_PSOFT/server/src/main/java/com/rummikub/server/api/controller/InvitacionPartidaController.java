package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.InvitacionPartidaDTO;
import com.rummikub.server.api.dto.invitacion.CreateInvitacionPartidaRequest;
import com.rummikub.server.api.dto.invitacion.InvitacionesPendientesResponse;
import com.rummikub.server.application.services.AuthService;
import com.rummikub.server.application.services.InvitacionPartidaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/invitaciones")
@CrossOrigin(origins = "*")
public class InvitacionPartidaController {

    private final InvitacionPartidaService invitacionPartidaService;
    private final AuthService authService;

    public InvitacionPartidaController(InvitacionPartidaService invitacionPartidaService, AuthService authService) {
        this.invitacionPartidaService = invitacionPartidaService;
        this.authService = authService;
    }

    @GetMapping
    public Object getAll(
            @RequestParam(required = false) Integer idInvitado,
            @RequestParam(required = false, defaultValue = "false") boolean includeInProgress) {
        if (includeInProgress) {
            if (idInvitado == null) {
                throw new IllegalArgumentException("idInvitado es obligatorio cuando includeInProgress=true");
            }
            InvitacionesPendientesResponse response = invitacionPartidaService
                    .getPendientesConPartidasEnCurso(idInvitado);
            return response;
        }
        if (idInvitado != null) {
            return invitacionPartidaService.getByInvitadoId(idInvitado);
        }
        return invitacionPartidaService.getAll();
    }

    @GetMapping("/invitado/{idInvitado}")
    public List<InvitacionPartidaDTO> getByInvitado(@PathVariable Integer idInvitado) {
        return invitacionPartidaService.getByInvitadoId(idInvitado);
    }

    @PostMapping
    public InvitacionPartidaDTO create(
            @Valid @RequestBody CreateInvitacionPartidaRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        Integer idEmisor = authService.requireUserId(authorizationHeader);
        return invitacionPartidaService.create(idEmisor, request.getIdInvitado(), request.getIdPartida());
    }

    @DeleteMapping("/{idEmisor}/{idInvitado}/{idPartida}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer idEmisor,
            @PathVariable Integer idInvitado,
            @PathVariable Integer idPartida,
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.assertSessionOwner(authorizationHeader, idEmisor);
        invitacionPartidaService.delete(idEmisor, idInvitado, idPartida);
        return ResponseEntity.noContent().build();
    }
}
