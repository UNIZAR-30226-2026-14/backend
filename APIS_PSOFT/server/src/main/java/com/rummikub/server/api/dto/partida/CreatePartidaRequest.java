package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CreatePartidaRequest {
    @Min(value = 0, message = "El turno no puede ser negativo")
    private int turno;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private String mercado;
    private String bolsa;

    @Size(max = 5000, message = "El conjuntoMesa supera el tamano permitido")
    private String conjuntoMesa;

    private boolean corriendo;

    public int getTurno() {
        return turno;
    }

    public void setTurno(int turno) {
        this.turno = turno;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getMercado() {
        return mercado;
    }

    public void setMercado(String mercado) {
        this.mercado = mercado;
    }

    public String getBolsa() {
        return bolsa;
    }

    public void setBolsa(String bolsa) {
        this.bolsa = bolsa;
    }

    public boolean isCorriendo() {
        return corriendo;
    }

    public void setCorriendo(boolean corriendo) {
        this.corriendo = corriendo;
    }

    public String getConjuntoMesa() {
        return conjuntoMesa;
    }

    public void setConjuntoMesa(String conjuntoMesa) {
        this.conjuntoMesa = conjuntoMesa;
    }
}
