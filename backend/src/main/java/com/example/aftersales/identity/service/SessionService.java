package com.example.aftersales.identity.service;

import com.example.aftersales.identity.cache.CachedSession;
import com.example.aftersales.identity.cache.SessionCache;
import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.domain.po.UserPO;
import com.example.aftersales.identity.domain.po.UserSessionPO;
import com.example.aftersales.identity.domain.vo.SessionVO;
import com.example.aftersales.identity.domain.vo.UserVO;
import com.example.aftersales.identity.mapper.UserMapper;
import com.example.aftersales.identity.mapper.UserSessionMapper;
import com.example.aftersales.identity.security.TokenCodec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class SessionService {

    // 未找到用户时也执行密码校验，降低通过耗时枚举账号的风险。
    private static final String DUMMY_HASH =
        "{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80";
    private final UserMapper users;
    private final UserSessionMapper sessions;
    private final SessionCache cache;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;
    private final Duration ttl;

    public SessionService(
        UserMapper users,
        UserSessionMapper sessions,
        SessionCache cache,
        PasswordEncoder passwords,
        PlatformTransactionManager manager,
        @Value("${app.auth.session-ttl:2h}") Duration ttl
    ) {
        this.users = users;
        this.sessions = sessions;
        this.cache = cache;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(manager);
        if (ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Session TTL must be between 1s and 1d");
        }
        this.ttl = ttl;
    }

    public SessionVO login(CreateSessionDTO request) {
        try {
            UserPO user = users.findByUsername(request.username());
            boolean matches;
            try {
                matches = passwords.matches(request.password(), user == null ? DUMMY_HASH : user.getPasswordHash());
            } catch (IllegalArgumentException ex) {
                matches = false;
            }
            if (
                user == null ||
                !matches ||
                !Boolean.TRUE.equals(user.getEnabled()) ||
                !request.username().equals(request.username().strip())
            ) {
                throw new AuthFailure(401, "AUTHENTICATION_FAILED", "用户名或密码错误");
            }
            String token = TokenCodec.generate();
            String hash = TokenCodec.hash(token);
            cache.lookup(hash); // 在写数据库前验证 Redis 可用。
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            var session = new UserSessionPO();
            session.setUserId(user.getId());
            session.setTokenHash(TokenCodec.bytes(hash));
            session.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            session.setExpiresAt(LocalDateTime.ofInstant(now.plus(ttl), ZoneOffset.UTC));
            transaction.executeWithoutResult(status -> sessions.insert(session));
            var cached = snapshot(session, user);
            try {
                if (!cache.putIfNotRevoked(hash, cached)) throw AuthFailure.unavailable();
            } catch (RuntimeException ex) {
                // 明文 Token 尚未交给客户端；尽力撤销已提交的孤立会话。
                transaction.executeWithoutResult(status ->
                    sessions.revokeByTokenHash(session.getTokenHash(), utcNow())
                );
                throw AuthFailure.unavailable();
            }
            return new SessionVO(token, "Bearer", cached.expiresAt().atOffset(ZoneOffset.UTC), userView(cached));
        } catch (AuthFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw AuthFailure.unavailable();
        }
    }

    public CachedSession authenticate(String token) {
        String hash = TokenCodec.hash(token);
        try {
            var lookup = cache.lookup(hash);
            if (lookup.state() == SessionCache.State.REVOKED) throw AuthFailure.unauthenticated();
            if (lookup.state() == SessionCache.State.HIT) return lookup.session();
            var session = sessions.findByTokenHash(TokenCodec.bytes(hash));
            requireUnexpired(session);
            if (session.getRevokedAt() != null) throw AuthFailure.unauthenticated();
            var user = users.findById(session.getUserId());
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) throw AuthFailure.unauthenticated();
            var cached = snapshot(session, user);
            // 回填时可能被并发注销抢先写入撤销标记，不能继续放行。
            if (!cache.putIfNotRevoked(hash, cached)) throw AuthFailure.unauthenticated();
            return cached;
        } catch (AuthFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw AuthFailure.unavailable();
        }
    }

    public void logout(String token) {
        String hash = TokenCodec.hash(token);
        try {
            var session = sessions.findByTokenHash(TokenCodec.bytes(hash));
            requireUnexpired(session);
            // 不依赖普通认证：已撤销但未过期的同一 Token 仍能重复注销。
            cache.revoke(hash, session.getExpiresAt().toInstant(ZoneOffset.UTC));
            transaction.executeWithoutResult(status -> sessions.revokeByTokenHash(session.getTokenHash(), utcNow()));
        } catch (AuthFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw AuthFailure.unavailable();
        }
    }

    private void requireUnexpired(UserSessionPO session) {
        if (session == null || !session.getExpiresAt().isAfter(utcNow())) throw AuthFailure.unauthenticated();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private CachedSession snapshot(UserSessionPO session, UserPO user) {
        UserVO.Role.valueOf(user.getRole());
        return new CachedSession(
            session.getId(),
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getRole(),
            session.getExpiresAt().toInstant(ZoneOffset.UTC)
        );
    }

    public static UserVO userView(CachedSession session) {
        return new UserVO(
            Long.toString(session.userId()),
            session.username(),
            session.displayName(),
            UserVO.Role.valueOf(session.role())
        );
    }
}
