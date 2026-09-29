package com.example.aftersales.identity.cache;

import java.time.Instant;

/** 仅包含认证所需字段；不保存 Token 明文或密码摘要。 */
public record CachedSession(
    long sessionId,
    long userId,
    String username,
    String displayName,
    String role,
    Instant expiresAt
) {}
