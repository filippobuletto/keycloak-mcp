package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/**
 * Profile fields for creating or updating a user. A null field is left unchanged; supplied attributes are merged
 * key by key.
 */
public record UserInput(
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Map<String, List<String>> attributes) {
}
