package com.example.aftersales.identity.security;

import com.example.aftersales.common.domain.vo.ApiError;
import com.example.aftersales.common.filter.RequestIdFilter;
import com.example.aftersales.identity.service.AuthFailure;
import com.example.aftersales.identity.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    UserDetailsService noDefaultPasswordLogin() {
        // 只允许业务登录端点，不创建 Spring Boot 默认测试账号。
        return username -> {
            throw new UsernameNotFoundException("Use session login endpoint");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ObjectProvider<SessionService> services,
        @Value("${app.auth.enabled:false}") boolean enabled
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!enabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            SessionService service = services.getObject();
            http.authorizeHttpRequests(auth ->
                auth
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/sessions")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/sessions/current")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            );
            http.addFilterBefore(new BearerFilter(service), UsernamePasswordAuthenticationFilter.class);
        }
        http.exceptionHandling(errors ->
            errors
                .authenticationEntryPoint((req, res, ex) -> write(req, res, AuthFailure.unauthenticated()))
                .accessDeniedHandler((req, res, ex) ->
                    write(req, res, new AuthFailure(403, "FORBIDDEN", "无权执行此操作"))
                )
        );
        return http.build();
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, AuthFailure failure)
        throws IOException {
        response.setStatus(failure.status());
        response.setContentType("application/json;charset=UTF-8");
        if (failure.status() == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setHeader("Cache-Control", "no-store");
        response
            .getWriter()
            .write(
                JSON.writeValueAsString(
                    new ApiError(
                        failure.status(),
                        failure.code(),
                        failure.getMessage(),
                        request.getRequestURI(),
                        (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
                        List.of()
                    )
                )
            );
    }

    private static class BearerFilter extends OncePerRequestFilter {

        private final SessionService service;

        BearerFilter(SessionService service) {
            this.service = service;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getServletPath();
            return (
                !path.startsWith("/api/") ||
                (path.equals("/api/sessions") && request.getMethod().equals("POST")) ||
                (path.equals("/api/sessions/current") && request.getMethod().equals("DELETE"))
            );
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            try {
                var principal = service.authenticate(TokenCodec.fromRequest(request));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                    )
                );
                SecurityContextHolder.setContext(context);
            } catch (AuthFailure failure) {
                write(request, response, failure);
                return;
            }
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
