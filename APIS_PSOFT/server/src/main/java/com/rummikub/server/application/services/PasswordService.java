package com.rummikub.server.application.services;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PasswordService {

    private static final String HASH_PREFIX = "sha256:";

    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("La contrasena no puede ser null");
        }
        return HASH_PREFIX + sha256Hex(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isHashed(storedPassword)) {
            return storedPassword.equals(hash(rawPassword));
        }
        return storedPassword.equals(rawPassword);
    }

    public boolean isHashed(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(HASH_PREFIX);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte current : bytes) {
                sb.append(String.format("%02x", current));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo inicializar SHA-256");
        }
    }
}
