package com.rummikub.server.api.dto.bot;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class BotItemUseDTO {

    private String item;

    @JsonProperty("target_player_idx")
    private Integer targetPlayerIdx;

    private String reason;

    private Map<String, Object> params;

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public Integer getTargetPlayerIdx() {
        return targetPlayerIdx;
    }

    public void setTargetPlayerIdx(Integer targetPlayerIdx) {
        this.targetPlayerIdx = targetPlayerIdx;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
