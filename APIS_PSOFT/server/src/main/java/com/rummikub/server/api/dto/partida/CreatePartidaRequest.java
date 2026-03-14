package com.rummikub.server.api.dto.partida;

public class CreatePartidaRequest {
    private String id;
    private int turno;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getTurno() {
        return turno;
    }

    public void setTurno(int turno) {
        this.turno = turno;
    }
}
