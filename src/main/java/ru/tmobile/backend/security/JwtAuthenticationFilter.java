package ru.tmobile.backend.security;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.tmobile.backend.dto.ErrorResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/health",
        "/api/v1/auth/request-code",
        "/api/v1/auth/verify-code",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    );

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED", "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!tokenProvider.validateAccessToken(token)) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED", "Invalid or expired JWT token");
            return;
        }

        try {
            Long userId = tokenProvider.extractUserId(token);
            String role = tokenProvider.extractRole(token);

            AuthContext.set(new AuthContext.UserContext(userId, role));

            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeErrorResponse(HttpServletResponse response, int status,
                                    String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse error = ErrorResponse.of(errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
