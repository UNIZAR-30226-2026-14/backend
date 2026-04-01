package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.bot.BotHealthResponse;
import com.rummikub.server.api.dto.bot.BotMoveRequest;
import com.rummikub.server.api.dto.bot.BotMoveResponse;
import com.rummikub.server.application.services.BotIntegrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final BotIntegrationService botIntegrationService;

    public BotController(BotIntegrationService botIntegrationService) {
        this.botIntegrationService = botIntegrationService;
    }

    @GetMapping("/health")
    public BotHealthResponse health() {
        return botIntegrationService.checkHealth();
    }

    @PostMapping("/move")
    public BotMoveResponse move(@Valid @RequestBody BotMoveRequest request) {
        return botIntegrationService.askMove(request);
    }
}
