package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/** {@code createdTimestamp} is epoch milliseconds. */
public record UserDetail(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        Long createdTimestamp,
        Map<String, List<String>> attributes,
        List<String> requiredActions) {
}
