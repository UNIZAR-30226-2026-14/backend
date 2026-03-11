package com.rummikub.server.api.dto.game;

public class CreateGameResponse {
    private String gameId;

    public CreateGameResponse() {
    }

    public CreateGameResponse(String gameId) {
        this.gameId = gameId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
}
