package org.example.ai_study_notes.agent.auth;

/**
 * 当前登录用户上下文（请求级 ThreadLocal）。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public record CurrentUser(Long id, String username, String role) {
    }

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    public static String username() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    public static String role() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
