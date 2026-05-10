package com.rummikub.server.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Supabase, Render y otros exponen {@code DATABASE_URL} como {@code postgres://} / {@code postgresql://}.
 * El driver JDBC solo acepta {@code jdbc:postgresql://...}. Esta clase adapta la URL y extrae usuario/contraseña.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "databaseUrlDerived";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                System.getenv("DATABASE_URL"));
        if (raw == null || raw.isBlank()) {
            return;
        }
        raw = raw.trim();

        Map<String, Object> map = new LinkedHashMap<>();

        if (raw.startsWith("jdbc:")) {
            String normalized = ensureSslModeForCloudProviders(raw);
            if (!normalized.equals(raw)) {
                map.put("spring.datasource.url", normalized);
            }
            if (!map.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
            }
            return;
        }

        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            return;
        }

        Parsed parsed = parsePostgresUri(raw);
        map.put("spring.datasource.url", ensureSslModeForCloudProviders(parsed.jdbcUrl()));
        if (parsed.username() != null) {
            map.put("spring.datasource.username", parsed.username());
        }
        if (parsed.password() != null) {
            map.put("spring.datasource.password", parsed.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }

    /**
     * Supabase/Neon suelen necesitar TLS explícito en JDBC si la URI no trae parámetros.
     */
    private static String ensureSslModeForCloudProviders(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        String url = jdbcUrl;
        String lower = url.toLowerCase(Locale.ROOT);

        boolean cloud = lower.contains("supabase") || lower.contains("neon.tech");
        if (cloud && !lower.contains("sslmode=")) {
            url += (url.contains("?") ? "&" : "?") + "sslmode=require";
            lower = url.toLowerCase(Locale.ROOT);
        }

        // Pooler transaccional de Supabase (puerto 6543): prepared statements desactivados
        if (lower.contains("supabase") && lower.contains(":6543") && !lower.contains("preparethreshold=")) {
            url += (url.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }
        return url;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static Parsed parsePostgresUri(String databaseUrl) {
        String forUri = databaseUrl.replaceFirst("^postgres(ql)?://", "http://");
        URI uri = URI.create(forUri);
        String userInfo = uri.getRawUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                username = urlDecode(userInfo.substring(0, colon));
                password = urlDecode(userInfo.substring(colon + 1));
            } else {
                username = urlDecode(userInfo);
            }
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (port < 0) {
            port = 5432;
        }
        String path = uri.getPath();
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + (path != null ? path : "");
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            jdbcUrl += "?" + query;
        }
        return new Parsed(jdbcUrl, username, password);
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private record Parsed(String jdbcUrl, String username, String password) {}

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
