package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class UpdatePartidaRequest {
    @Min(value = 0, message = "El turno no puede ser negativo")
    private int turno;

    @Size(max = 5000, message = "La bolsa supera el tamano permitido")
    private String bolsa;

    @Size(max = 5000, message = "El mercado supera el tamano permitido")
    private String mercado;

    private String fichasJugador1;
    private String fichasJugador2;
    private String fichasJugador3;
    private String fichasJugador4;
    private String habilidadesJugador1;
    private String habilidadesJugador2;
    private String habilidadesJugador3;
    private String habilidadesJugador4;

    public int getTurno() {
        return turno;
    }

    public void setTurno(int turno) {
        this.turno = turno;
    }

    public String getBolsa() {
        return bolsa;
    }

    public void setBolsa(String bolsa) {
        this.bolsa = bolsa;
    }

    public String getMercado() {
        return mercado;
    }

    public void setMercado(String mercado) {
        this.mercado = mercado;
    }

    public String getFichasJugador1() {
        return fichasJugador1;
    }

    public void setFichasJugador1(String fichasJugador1) {
        this.fichasJugador1 = fichasJugador1;
    }

    public String getFichasJugador2() {
        return fichasJugador2;
    }

    public void setFichasJugador2(String fichasJugador2) {
        this.fichasJugador2 = fichasJugador2;
    }

    public String getFichasJugador3() {
        return fichasJugador3;
    }

    public void setFichasJugador3(String fichasJugador3) {
        this.fichasJugador3 = fichasJugador3;
    }

    public String getFichasJugador4() {
        return fichasJugador4;
    }

    public void setFichasJugador4(String fichasJugador4) {
        this.fichasJugador4 = fichasJugador4;
    }

    public String getHabilidadesJugador1() {
        return habilidadesJugador1;
    }

    public void setHabilidadesJugador1(String habilidadesJugador1) {
        this.habilidadesJugador1 = habilidadesJugador1;
    }

    public String getHabilidadesJugador2() {
        return habilidadesJugador2;
    }

    public void setHabilidadesJugador2(String habilidadesJugador2) {
        this.habilidadesJugador2 = habilidadesJugador2;
    }

    public String getHabilidadesJugador3() {
        return habilidadesJugador3;
    }

    public void setHabilidadesJugador3(String habilidadesJugador3) {
        this.habilidadesJugador3 = habilidadesJugador3;
    }

    public String getHabilidadesJugador4() {
        return habilidadesJugador4;
    }

    public void setHabilidadesJugador4(String habilidadesJugador4) {
        this.habilidadesJugador4 = habilidadesJugador4;
    }
}
