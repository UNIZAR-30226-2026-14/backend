package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.game.CommandRequest;
import com.rummikub.server.api.dto.game.CreateGameResponse;
import com.rummikub.server.api.dto.game.GameStateDTO;
import com.rummikub.server.api.dto.game.JoinGameRequest;
import com.rummikub.server.api.dto.game.PlayerDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GameService {

    private static final int MAX_PLAYERS = 4;

    private final Map<String, Game> games = new HashMap<>();

    public CreateGameResponse createGame() {
        String gameId = "game-" + UUID.randomUUID().toString().substring(0, 8);

        Game game = new Game();
        game.id = gameId;
        game.status = "OPEN";
        game.turnIndex = 0;
        game.players = new ArrayList<>();

        games.put(gameId, game);
        return new CreateGameResponse(gameId);
    }

    public GameStateDTO join(String gameId, JoinGameRequest request) {
        Game game = mustGetGame(gameId);

        if (!"OPEN".equals(game.status)) {
            throw new IllegalStateException("Game is not open");
        }

        if (game.players.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Game is full");
        }

        boolean exists = game.players.stream()
                .anyMatch(player -> player.playerId.equals(request.getPlayerId()));

        if (!exists) {
            game.players.add(new Player(request.getPlayerId(), request.getName()));
        }

        return formatState(game);
    }

    public GameStateDTO command(String gameId, CommandRequest request) {
        Game game = mustGetGame(gameId);

        if (game.players.isEmpty()) {
            throw new IllegalStateException("No players in game");
        }

        if ("START".equals(request.getType())) {
            game.status = "RUNNING";
            game.turnIndex = 0;
        } else if ("END_TURN".equals(request.getType())) {
            if (!"RUNNING".equals(game.status)) {
                throw new IllegalStateException("Game not running");
            }
            String currentTurnPlayerId = game.players.get(game.turnIndex).playerId;
            if (!currentTurnPlayerId.equals(request.getPlayerId())) {
                throw new IllegalStateException("Not your turn");
            }
            game.turnIndex = (game.turnIndex + 1) % game.players.size();
        } else {
            throw new IllegalArgumentException("Unknown command type: " + request.getType());
        }

        return formatState(game);
    }

    public GameStateDTO state(String gameId) {
        return formatState(mustGetGame(gameId));
    }

    private Game mustGetGame(String gameId) {
        Game game = games.get(gameId);
        if (game == null) {
            throw new NoSuchElementException("Game not found: " + gameId);
        }
        return game;
    }

    private GameStateDTO formatState(Game game) {
        GameStateDTO state = new GameStateDTO();
        state.setGameId(game.id);
        state.setStatus(game.status);

        List<PlayerDTO> players = new ArrayList<>();
        for (Player player : game.players) {
            players.add(new PlayerDTO(player.playerId, player.name));
        }
        state.setPlayers(players);

        if (!game.players.isEmpty()) {
            state.setTurnPlayerId(game.players.get(game.turnIndex).playerId);
        }

        return state;
    }

    private static class Game {
        private String id;
        private String status;
        private int turnIndex;
        private List<Player> players;
    }

    private static class Player {
        private String playerId;
        private String name;

        private Player(String playerId, String name) {
            this.playerId = playerId;
            this.name = name;
        }
    }
}
