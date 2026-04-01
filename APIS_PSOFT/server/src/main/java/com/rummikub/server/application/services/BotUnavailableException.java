package com.rummikub.server.application.services;

public class BotUnavailableException extends RuntimeException {
    public BotUnavailableException(String message) {
        super(message);
    }
}
