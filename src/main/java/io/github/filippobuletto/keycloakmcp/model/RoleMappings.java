package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/** Roles mapped directly to a user, a service account or a client scope; {@code clientRoles} is keyed by clientId. */
public record RoleMappings(List<String> realmRoles, Map<String, List<String>> clientRoles) {
}
