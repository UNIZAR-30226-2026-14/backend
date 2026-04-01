package com.rummikub.server.api.dto.bot;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BotMoveResponse {

    private BotMoveDTO move;

    @JsonProperty("move_short")
    private String moveShort;

    public BotMoveDTO getMove() {
        return move;
    }

    public void setMove(BotMoveDTO move) {
        this.move = move;
    }

    public String getMoveShort() {
        return moveShort;
    }

    public void setMoveShort(String moveShort) {
        this.moveShort = moveShort;
    }
}
