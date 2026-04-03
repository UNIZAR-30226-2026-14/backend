package com.rummikub.server.api.dto.game;

import java.util.List;

public class GameStateDTO {
    private String gameId;
    private String status;
    private String turnPlayerId;
    private List<PlayerDTO> players;

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTurnPlayerId() {
        return turnPlayerId;
    }

    public void setTurnPlayerId(String turnPlayerId) {
        this.turnPlayerId = turnPlayerId;
    }

    public List<PlayerDTO> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerDTO> players) {
        this.players = players;
    }
}
