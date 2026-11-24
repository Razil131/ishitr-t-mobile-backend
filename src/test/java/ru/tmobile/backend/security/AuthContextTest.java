package ru.tmobile.backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthContextTest {

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    void setAndGetUserContext() {
        AuthContext.UserContext ctx = new AuthContext.UserContext(1L, "OWNER");
        AuthContext.set(ctx);

        assertEquals(1L, AuthContext.getUserId());
        assertEquals("OWNER", AuthContext.getRole());
        assertSame(ctx, AuthContext.get());
    }

    @Test
    void returnsNullWhenNotSet() {
        assertNull(AuthContext.getUserId());
        assertNull(AuthContext.getRole());
        assertNull(AuthContext.get());
    }

    @Test
    void clearRemovesContext() {
        AuthContext.set(new AuthContext.UserContext(2L, "MEMBER"));
        assertNotNull(AuthContext.get());

        AuthContext.clear();
        assertNull(AuthContext.get());
    }
}
