package com.rummikub.server.api.dto.bot;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class BotMoveDTO {

    @JsonProperty("move_type")
    private String moveType;

    private String reason;

    @JsonProperty("new_melds")
    private List<List<String>> newMelds;

    @JsonProperty("extend_index")
    private Integer extendIndex;

    @JsonProperty("extension_tiles")
    private List<String> extensionTiles;

    @JsonProperty("new_board")
    private List<List<String>> newBoard;

    @JsonProperty("item_use")
    private BotItemUseDTO itemUse;

    @JsonProperty("shop_choice")
    private BotShopChoiceDTO shopChoice;

    public String getMoveType() {
        return moveType;
    }

    public void setMoveType(String moveType) {
        this.moveType = moveType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<List<String>> getNewMelds() {
        return newMelds;
    }

    public void setNewMelds(List<List<String>> newMelds) {
        this.newMelds = newMelds;
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

    public BotItemUseDTO getItemUse() {
        return itemUse;
    }

    public void setItemUse(BotItemUseDTO itemUse) {
        this.itemUse = itemUse;
    }

    public BotShopChoiceDTO getShopChoice() {
        return shopChoice;
    }

    public void setShopChoice(BotShopChoiceDTO shopChoice) {
        this.shopChoice = shopChoice;
    }
}
