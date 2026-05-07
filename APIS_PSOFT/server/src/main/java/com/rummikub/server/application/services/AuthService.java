package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.JugadorDTO;
import com.rummikub.server.api.dto.auth.LoginRequest;
import com.rummikub.server.api.dto.auth.LoginResponse;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int SESSION_TTL_HOURS = 12;

    private final JugadorRepository jugadorRepository;
    private final PasswordService passwordService;
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public AuthService(JugadorRepository jugadorRepository, PasswordService passwordService) {
        this.jugadorRepository = jugadorRepository;
        this.passwordService = passwordService;
    }

    public LoginResponse login(LoginRequest request) {
        List<JugadorEntity> jugadores = jugadorRepository.findAllByNombreIgnoreCase(request.getNombre());
        if (jugadores.isEmpty()) {
            throw new NoSuchElementException("Usuario no encontrado");
        }
        if (jugadores.size() > 1) {
            throw new IllegalStateException("Hay varios usuarios con ese nombre. Contacta con soporte.");
        }
        JugadorEntity jugador = jugadores.get(0);

        if (!passwordService.matches(request.getContrasena(), jugador.getContrasena())) {
            throw new SecurityException("Credenciales invalidas");
        }
        if (!passwordService.isHashed(jugador.getContrasena())) {
            jugador.setContrasena(passwordService.hash(request.getContrasena()));
            jugadorRepository.save(jugador);
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(SESSION_TTL_HOURS);
        sessions.put(token, new SessionData(jugador.getId(), expiresAt));

        return new LoginResponse(token, expiresAt, Mapper.toDTO(jugador));
    }

    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        sessions.remove(token);
    }

    public JugadorDTO me(String authorizationHeader) {
        Integer idJugador = requireUserId(authorizationHeader);
        JugadorEntity jugador = jugadorRepository.findById(idJugador)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + idJugador));
        return Mapper.toDTO(jugador);
    }

    public Integer requireUserId(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        SessionData session = sessions.get(token);
        if (session == null) {
            throw new SecurityException("Sesion no valida");
        }

        if (session.expiresAt.isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            throw new SecurityException("Sesion expirada");
        }
        return session.idJugador;
    }

    public void assertSessionOwner(String authorizationHeader, Integer idJugadorObjetivo) {
        Integer idSesion = requireUserId(authorizationHeader);
        if (!idSesion.equals(idJugadorObjetivo)) {
            throw new SecurityException("No puedes actuar en nombre de otro jugador");
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    public void clearSessions() {
        sessions.clear();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new SecurityException("Falta cabecera Authorization");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix)) {
            throw new SecurityException("Formato Authorization invalido. Usa Bearer <token>");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isEmpty()) {
            throw new SecurityException("Token vacio");
        }
        return token;
    }

    private static final class SessionData {
        private final Integer idJugador;
        private final LocalDateTime expiresAt;

        private SessionData(Integer idJugador, LocalDateTime expiresAt) {
            this.idJugador = idJugador;
            this.expiresAt = expiresAt;
        }
    }
}
