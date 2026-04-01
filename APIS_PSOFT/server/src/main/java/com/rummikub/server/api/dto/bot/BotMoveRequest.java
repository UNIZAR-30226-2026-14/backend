package com.rummikub.server.api.dto.bot;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class BotMoveRequest {

    @NotNull(message = "El tablero es obligatorio")
    private List<List<String>> board;

    @NotNull(message = "pool_count es obligatorio")
    @JsonProperty("pool_count")
    private Integer poolCount;

    @NotNull(message = "my_tiles es obligatorio")
    @JsonProperty("my_tiles")
    private List<String> myTiles;

    @JsonProperty("opponent_rack_counts")
    private List<Integer> opponentRackCounts;

    private Boolean opened;
    private Integer level;
    private Double randomness;
    private Integer seed;

    @JsonProperty("turn_number")
    private Integer turnNumber;

    public List<List<String>> getBoard() {
        return board;
    }

    public void setBoard(List<List<String>> board) {
        this.board = board;
    }

    public Integer getPoolCount() {
        return poolCount;
    }

    public void setPoolCount(Integer poolCount) {
        this.poolCount = poolCount;
    }

    public List<String> getMyTiles() {
        return myTiles;
    }

    public void setMyTiles(List<String> myTiles) {
        this.myTiles = myTiles;
    }

    public List<Integer> getOpponentRackCounts() {
        return opponentRackCounts;
    }

    public void setOpponentRackCounts(List<Integer> opponentRackCounts) {
        this.opponentRackCounts = opponentRackCounts;
    }

    public Boolean getOpened() {
        return opened;
    }

    public void setOpened(Boolean opened) {
        this.opened = opened;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Double getRandomness() {
        return randomness;
    }

    public void setRandomness(Double randomness) {
        this.randomness = randomness;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public Integer getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(Integer turnNumber) {
        this.turnNumber = turnNumber;
    }
}
