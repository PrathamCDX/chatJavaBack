package com.example.demo.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Map<String, ?> claims) {
        Date now = new Date();
        return Jwts.builder()
                // Custom claims first so they can't override the reserved sub/iat/exp set below
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the token's verified payload (subject, custom claims, iat, exp);
     * throws JwtException if invalid or expired.
     */
    public Claims extractJwtData(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** True if the signature checks out and the token hasn't expired. */
    public boolean isTokenValid(String token) {
        try {
            extractJwtData(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: null/empty token, e.g. a bare "Bearer " header
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
