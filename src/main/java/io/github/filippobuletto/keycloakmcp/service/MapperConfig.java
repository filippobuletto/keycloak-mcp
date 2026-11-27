package io.github.filippobuletto.keycloakmcp.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.keycloak.representations.idm.ConfigPropertyRepresentation;

import io.quarkiverse.mcp.server.ToolCallException;

/** The config key check shared by client scope mappers, identity provider mappers and authenticator configs. */
final class MapperConfig {

    private MapperConfig() {
    }

    /** Checks only the supplied keys: stored mappers may hold keys their type no longer declares. */
    static void check(String typeId, List<ConfigPropertyRepresentation> properties, Set<String> keys) {
        check("mapper type", typeId, properties, keys);
    }

    /** As {@link #check(String, List, Set)}; {@code kind} names the type in the message, such as "mapper type". */
    static void check(String kind, String typeId, List<ConfigPropertyRepresentation> properties, Set<String> keys) {
        Set<String> declared = new TreeSet<>();
        if (properties != null) {
            properties.stream().map(ConfigPropertyRepresentation::getName).forEach(declared::add);
        }
        for (String key : new TreeSet<>(keys)) {
            if (!declared.contains(key)) {
                throw new ToolCallException("Config key " + key + " is not declared by " + kind + " " + typeId
                        + "; valid keys: " + String.join(", ", declared) + ". Nothing was changed.");
            }
        }
    }
}
