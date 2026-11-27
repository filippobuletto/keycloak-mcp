package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/**
 * A required action with its place in the order Keycloak runs them (1 runs first), its stored config, and the config
 * keys it takes; {@code configProperties} is empty when the action takes no config.
 */
public record RequiredActionDetail(
        String alias,
        String name,
        boolean enabled,
        boolean defaultAction,
        String providerId,
        int place,
        Map<String, String> config,
        List<ProtocolMapperType.Property> configProperties) {
}
