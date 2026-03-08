package com.rummikub.server.api.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

//FUNCIONES ACTIVAS HASTA EL MOMENTO-->
/* 
->creacion de sala
->Uninon de jugadores 
    (a prueba de maximos, jugadores duplicados)
->Envios de comandos e instrucciones
    (de momento solo los turnos y su secuencializacion)
->Representacion y envio del estado del juego

COSAS IMPORTANTES A FUTURA IMPLEMENTACION

->Definir puerto de entrada para servidor, clientes y BBDD
->Enlazar API con BBDD y adaptar los envios de datos
->Creacion de comunicacion con el mercado


FUNCIONES A TESTEAR
->Cuando se pueda comunicacion con nodos
->Pruebas de acceso a usuarios(maximos, duplicados, erroneos, caidas)
->Pruebas de correlacion de datos recibo/envio
->

*/

@RestController
@RequestMapping("/api/games")
public class GameController {

// TIPOS A USAR EN LA COMUNICACION
    public static class CreateGameRequest {
        public String hostPlayerId; // no lo usamos aún, pero lo dejamos
    }

    public static class CreateGameResponse {
        public String gameId;
        public CreateGameResponse(String gameId) { this.gameId = gameId; }
    }

    public static class JoinGameRequest {
        public String playerId;
        public String name;
    }

    public static class CommandRequest {
        public String playerId;
        public String type; // "START" | "END_TURN"
    }

    public static class GameState {
        public String gameId;
        public String status;
        public String turnPlayerId;
        public List<Player> players;
    }

    public static class Game {
        public String id;
        public String status;   // OPEN, RUNNING
        public int turnIndex;
        public List<Player> players;
    }

    public static class Player {
        public String playerId;
        public String name;

        public Player() {}
        public Player(String playerId, String name) {
            this.playerId = playerId;
            this.name = name;
        }
    }


    // "BBDD" en memoria (para practicar)
    private final Map<String, Game> games = new HashMap<>();

    // 1) Crear partida
    @PostMapping
    public CreateGameResponse createGame(@RequestBody CreateGameRequest req) {
        String gameId = "game-" + UUID.randomUUID().toString().substring(0, 8);

        Game game = new Game();
        game.id = gameId;
        game.status = "OPEN";
        game.turnIndex = 0;
        game.players = new ArrayList<>();

        games.put(gameId, game);

        return new CreateGameResponse(gameId);
    }

    // 2) Unirse a partida
    @PostMapping("/{gameId}/join")
    public GameState join(@PathVariable String gameId, @RequestBody JoinGameRequest req) {
        Game game = mustGetGame(gameId);

        if (!"OPEN".equals(game.status)) {
            throw new IllegalStateException("Game is not open");
        }

        if (game.players.size() >= 4) {
            throw new IllegalStateException("Game is full");
        }

        // evitar jugadorrs duplicados
        boolean exists = false;
        for (Player p : game.players) {
            if (p.playerId.equals(req.playerId)) exists = true;
        }
        if (!exists) {
            game.players.add(new Player(req.playerId, req.name));
        }

        return FormatoEstado(game);
    }

    // 3) Mandar instrucción (comunicación)
    @PostMapping("/{gameId}/command")
    public GameState command(@PathVariable String gameId, @RequestBody CommandRequest req) {
        Game game = mustGetGame(gameId);

        if (game.players.isEmpty()) {
            throw new IllegalStateException("No players in game");
        }

        // Solo para practicar: dos comandos
        if ("START".equals(req.type)) {
            game.status = "RUNNING";
            game.turnIndex = 0;
        } else if ("END_TURN".equals(req.type)) {
            if (!"RUNNING".equals(game.status)) {
                throw new IllegalStateException("Game not running");
            }
            String currentTurnPlayerId = game.players.get(game.turnIndex).playerId;
            if (!currentTurnPlayerId.equals(req.playerId)) {
                throw new IllegalStateException("Not your turn");
            }
            game.turnIndex = (game.turnIndex + 1) % game.players.size();
        } else {
            throw new IllegalArgumentException("Unknown command type: " + req.type);
        }

        return FormatoEstado(game);
    }

    // 4) Consultar estado
    @GetMapping("/{gameId}")
    public GameState state(@PathVariable String gameId) {
        Game game = mustGetGame(gameId);
        return FormatoEstado(game);
    }

    // funciones auxiliares

    private Game mustGetGame(String gameId) {
        Game g = games.get(gameId);
        if (g == null) throw new NoSuchElementException("Game not found: " + gameId);
        return g;
    }

    private GameState FormatoEstado(Game game) {
        GameState st = new GameState();
        st.gameId = game.id;
        st.status = game.status;
        st.players = new ArrayList<>();
        for (Player p : game.players) {
            st.players.add(p);
        }
        if (!game.players.isEmpty()) {
            st.turnPlayerId = game.players.get(game.turnIndex).playerId;
        } else {
            st.turnPlayerId = null;
        }
        return st;
    }

}