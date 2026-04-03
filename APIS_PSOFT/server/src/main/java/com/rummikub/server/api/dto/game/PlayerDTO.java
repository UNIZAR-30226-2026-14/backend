package com.rummikub.server.api.dto.game;

public class PlayerDTO {
    private String playerId;
    private String name;

    public PlayerDTO() {
    }

    public PlayerDTO(String playerId, String name) {
        this.playerId = playerId;
        this.name = name;
    }

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
