package com.rummikub.server.api.dto.bot;

public class BotShopChoiceDTO {

    private String buy;
    private String reason;

    public String getBuy() {
        return buy;
    }

    public void setBuy(String buy) {
        this.buy = buy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
