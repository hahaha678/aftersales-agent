package com.example.aftersales.identity.service;

import com.example.aftersales.identity.cache.CachedSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 提供给业务模块的身份入口，不接受客户端指定身份。 */
@Service
public class CurrentUserService {
    public void requireStaff() {
        requireUserId();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) {
            throw new AuthFailure(403, "FORBIDDEN", "此操作仅限客服");
        }
    }
    public long requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CachedSession session)) {
            throw AuthFailure.unauthenticated();
        }
        return session.userId();
    }
}
