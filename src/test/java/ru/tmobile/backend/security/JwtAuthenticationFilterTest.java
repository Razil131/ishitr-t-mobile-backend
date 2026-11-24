package ru.tmobile.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new JwtAuthenticationFilter(tokenProvider, objectMapper);
    }

    @Test
    void shouldNotFilter_publicHealthEndpoint() {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_publicAuthEndpoints() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/request-code");
        assertTrue(filter.shouldNotFilter(request));
        when(request.getRequestURI()).thenReturn("/api/v1/auth/verify-code");
        assertTrue(filter.shouldNotFilter(request));
        when(request.getRequestURI()).thenReturn("/api/v1/auth/refresh");
        assertTrue(filter.shouldNotFilter(request));
        when(request.getRequestURI()).thenReturn("/api/v1/auth/logout");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilter_protectedEndpoints() {
        when(request.getRequestURI()).thenReturn("/api/v1/groups");
        assertFalse(filter.shouldNotFilter(request));
        when(request.getRequestURI()).thenReturn("/api/v1/users/active");
        assertFalse(filter.shouldNotFilter(request));
        when(request.getRequestURI()).thenReturn("/api/v1/tariffs");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void doFilterInternal_returns401WhenHeaderMissing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        assertTrue(body.toString().contains("UNAUTHORIZED"));
        verifyNoInteractions(filterChain, tokenProvider);
    }

    @Test
    void doFilterInternal_returns401WhenHeaderIsNotBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abcdef");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        assertTrue(body.toString().contains("UNAUTHORIZED"));
        verifyNoInteractions(filterChain, tokenProvider);
    }

    @Test
    void doFilterInternal_returns401WhenTokenInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.value");
        when(tokenProvider.validateAccessToken("bad.token.value")).thenReturn(false);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        assertTrue(body.toString().contains("UNAUTHORIZED"));
        verifyNoInteractions(filterChain);
        assertNull(AuthContext.get());
    }

    @Test
    void doFilterInternal_setsAuthContextAndPassesChainForValidToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer validToken");
        when(tokenProvider.validateAccessToken("validToken")).thenReturn(true);
        when(tokenProvider.extractUserId("validToken")).thenReturn(42L);
        when(tokenProvider.extractRole("validToken")).thenReturn("OWNER");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(AuthContext.get(), "AuthContext must be cleared after chain completes");
    }

    @Test
    void doFilterInternal_clearsAuthContextEvenIfChainThrows() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer validToken");
        when(tokenProvider.validateAccessToken("validToken")).thenReturn(true);
        when(tokenProvider.extractUserId("validToken")).thenReturn(42L);
        when(tokenProvider.extractRole("validToken")).thenReturn("OWNER");
        doThrow(new ServletException("boom")).when(filterChain).doFilter(request, response);

        assertThrows(ServletException.class,
            () -> filter.doFilterInternal(request, response, filterChain));
        assertNull(AuthContext.get(), "AuthContext must be cleared even on exception");
    }
}
