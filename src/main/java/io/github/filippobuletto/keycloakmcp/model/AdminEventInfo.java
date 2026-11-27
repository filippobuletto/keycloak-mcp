package io.github.filippobuletto.keycloakmcp.model;

/**
 * An admin event. {@code time} is epoch milliseconds, as Keycloak gives it. {@code authRealm}, {@code authClientId},
 * {@code authUserId} and {@code authIpAddress} say who acted; realm and client show by name, or by id when the name
 * no longer resolves. {@code representation} is the JSON sent, when asked for and recorded.
 */
public record AdminEventInfo(
        long time,
        String operationType,
        String resourceType,
        String resourcePath,
        String authRealm,
        String authClientId,
        String authUserId,
        String authIpAddress,
        String error,
        String representation) {
}
