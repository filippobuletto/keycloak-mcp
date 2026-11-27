package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/**
 * A user session. {@code start} and {@code lastAccess} are epoch milliseconds, as Keycloak gives them;
 * {@code clientIds} names the clients the session covers.
 */
public record SessionInfo(
        String id,
        SessionType type,
        String username,
        String userId,
        String ipAddress,
        long start,
        long lastAccess,
        boolean rememberMe,
        List<String> clientIds) {
}
