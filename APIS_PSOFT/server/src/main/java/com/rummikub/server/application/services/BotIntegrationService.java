package com.rummikub.server.application.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rummikub.server.api.dto.bot.BotErrorResponse;
import com.rummikub.server.api.dto.bot.BotHealthResponse;
import com.rummikub.server.api.dto.bot.BotMoveRequest;
import com.rummikub.server.api.dto.bot.BotMoveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class BotIntegrationService {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String movePath;
    private final String healthPath;
    private final HttpClient httpClient;
    private final Duration timeout;

    public BotIntegrationService(
            ObjectMapper objectMapper,
            @Value("${bot.ia.base-url:http://127.0.0.1:8765}") String baseUrl,
            @Value("${bot.ia.move-path:/api/bot/move}") String movePath,
            @Value("${bot.ia.health-path:/api/health}") String healthPath,
            @Value("${bot.ia.timeout-ms:5000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = removeFinalSlash(baseUrl);
        this.movePath = ensureLeadingSlash(movePath);
        this.healthPath = ensureLeadingSlash(healthPath);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public BotHealthResponse checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + healthPath))
                .timeout(timeout)
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new BotUnavailableException("La IA respondio con estado " + response.statusCode() + " en health");
        }

        try {
            return objectMapper.readValue(response.body(), BotHealthResponse.class);
        } catch (JsonProcessingException ex) {
            throw new BotUnavailableException("La IA devolvio una respuesta de health invalida");
        }
    }

    public BotMoveResponse askMove(BotMoveRequest payload) {
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("El payload de la jugada del bot es invalido");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + movePath))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = send(request);

        if (response.statusCode() == 200) {
            try {
                return objectMapper.readValue(response.body(), BotMoveResponse.class);
            } catch (JsonProcessingException ex) {
                throw new BotUnavailableException("La IA devolvio una jugada en formato invalido");
            }
        }

        if (response.statusCode() == 400) {
            String msg = "La IA rechazo la peticion";
            try {
                BotErrorResponse error = objectMapper.readValue(response.body(), BotErrorResponse.class);
                if (error.getError() != null && !error.getError().isBlank()) {
                    msg = error.getError();
                }
            } catch (JsonProcessingException ignored) {
            }
            throw new IllegalArgumentException(msg);
        }

        throw new BotUnavailableException("La IA devolvio estado " + response.statusCode());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new BotUnavailableException("No se pudo conectar con la IA: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BotUnavailableException("La llamada a la IA fue interrumpida");
        }
    }

    private String removeFinalSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String ensureLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }
}
