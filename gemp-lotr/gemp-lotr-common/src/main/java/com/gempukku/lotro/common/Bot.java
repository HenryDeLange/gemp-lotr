package com.gempukku.lotro.common;

public enum Bot {
    BOT_NAME("BOT"),
    BOT_DB_TYPE("bot");

    private String value;

    Bot(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
