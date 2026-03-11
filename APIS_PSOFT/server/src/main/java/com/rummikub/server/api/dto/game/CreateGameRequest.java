package com.rummikub.server.api.dto.game;

public class CreateGameRequest {
    private String hostPlayerId;

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public void setHostPlayerId(String hostPlayerId) {
        this.hostPlayerId = hostPlayerId;
    }
}
