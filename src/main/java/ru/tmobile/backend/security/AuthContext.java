package ru.tmobile.backend.security;

public final class AuthContext {

    public record UserContext(Long userId, String role) {}

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(UserContext ctx) {
        CURRENT.set(ctx);
    }

    public static UserContext get() {
        return CURRENT.get();
    }

    public static Long getUserId() {
        UserContext ctx = CURRENT.get();
        return ctx != null ? ctx.userId() : null;
    }

    public static String getRole() {
        UserContext ctx = CURRENT.get();
        return ctx != null ? ctx.role() : null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
