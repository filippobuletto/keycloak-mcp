package io.github.filippobuletto.keycloakmcp.model;

/** {@code subGroupCount} is null where Keycloak does not report it, as for a user's groups. */
public record GroupSummary(String id, String name, String path, Long subGroupCount) {
}
