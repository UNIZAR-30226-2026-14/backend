package com.rummikub.server.api.dto.game;

public class JoinGameRequest {
    private String playerId;
    private String name;

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
