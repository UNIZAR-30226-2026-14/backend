package com.rummikub.server.api.dto.partida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PlayAdvancedTurnRequest {

    @NotNull(message = "idJugador es obligatorio")
    private Integer idJugador;

    @NotBlank(message = "moveType es obligatorio")
    private String moveType;

    private List<List<String>> grupos;
    private Integer extendIndex;
    private List<String> extensionTiles;
    private List<List<String>> newBoard;

    public Integer getIdJugador() {
        return idJugador;
    }

    public void setIdJugador(Integer idJugador) {
        this.idJugador = idJugador;
    }

    public String getMoveType() {
        return moveType;
    }

    public void setMoveType(String moveType) {
        this.moveType = moveType;
    }

    public List<List<String>> getGrupos() {
        return grupos;
    }

    public void setGrupos(List<List<String>> grupos) {
        this.grupos = grupos;
    }

    public Integer getExtendIndex() {
        return extendIndex;
    }

    public void setExtendIndex(Integer extendIndex) {
        this.extendIndex = extendIndex;
    }

    public List<String> getExtensionTiles() {
        return extensionTiles;
    }

    public void setExtensionTiles(List<String> extensionTiles) {
        this.extensionTiles = extensionTiles;
    }

    public List<List<String>> getNewBoard() {
        return newBoard;
    }

    public void setNewBoard(List<List<String>> newBoard) {
        this.newBoard = newBoard;
    }
}
