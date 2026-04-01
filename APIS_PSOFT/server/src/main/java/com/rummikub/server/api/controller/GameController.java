package com.rummikub.server.api.controller;

import com.rummikub.server.api.dto.game.CommandRequest;
import com.rummikub.server.api.dto.game.CreateGameRequest;
import com.rummikub.server.api.dto.game.CreateGameResponse;
import com.rummikub.server.api.dto.game.GameStateDTO;
import com.rummikub.server.api.dto.game.JoinGameRequest;
import com.rummikub.server.application.services.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public CreateGameResponse createGame(@RequestBody CreateGameRequest request) {
        return gameService.createGame();
    }

    @PostMapping("/{gameId}/join")
    public GameStateDTO join(@PathVariable String gameId, @RequestBody JoinGameRequest request) {
        return gameService.join(gameId, request);
    }

    @PostMapping("/{gameId}/command")
    public GameStateDTO command(@PathVariable String gameId, @RequestBody CommandRequest request) {
        return gameService.command(gameId, request);
    }

    @GetMapping("/{gameId}")
    public GameStateDTO state(@PathVariable String gameId) {
        return gameService.state(gameId);
    }
}
