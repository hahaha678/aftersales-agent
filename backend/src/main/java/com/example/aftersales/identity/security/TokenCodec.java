package com.example.aftersales.identity.security;

import com.example.aftersales.identity.service.AuthFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;

public final class TokenCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenCodec() {}
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String fromRequest(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1) throw AuthFailure.unauthenticated();
        String value = headers.getFirst();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) throw AuthFailure.unauthenticated();
        String token = value.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw AuthFailure.unauthenticated();
        return token;
    }
    public static String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw AuthFailure.unauthenticated();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
    public static byte[] bytes(String hash) { return HexFormat.of().parseHex(hash); }
}
