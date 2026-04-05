package com.rummikub.server.api.dto.auth;

import com.rummikub.server.api.dto.JugadorDTO;

import java.time.LocalDateTime;

public class LoginResponse {

    private String token;
    private LocalDateTime expiraEn;
    private JugadorDTO jugador;

    public LoginResponse() {
    }

    public LoginResponse(String token, LocalDateTime expiraEn, JugadorDTO jugador) {
        this.token = token;
        this.expiraEn = expiraEn;
        this.jugador = jugador;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public JugadorDTO getJugador() {
        return jugador;
    }

    public void setJugador(JugadorDTO jugador) {
        this.jugador = jugador;
    }
}
