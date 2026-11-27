package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/** Roles mapped directly to the group; {@code clientRoles} is keyed by clientId. */
public record GroupDetail(
        String id,
        String name,
        String path,
        long subGroupCount,
        Map<String, List<String>> attributes,
        List<String> realmRoles,
        Map<String, List<String>> clientRoles) {
}
