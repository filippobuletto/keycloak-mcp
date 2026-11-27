package io.github.filippobuletto.keycloakmcp.model;

import java.util.Map;

/**
 * A login (user) event. {@code time} is epoch milliseconds, as Keycloak gives it; {@code error} is set on the _ERROR
 * types; {@code details} holds what Keycloak adds per type, such as username, grant_type or redirect_uri.
 */
public record EventInfo(
        long time,
        String type,
        String clientId,
        String userId,
        String sessionId,
        String ipAddress,
        String error,
        Map<String, String> details) {
}
