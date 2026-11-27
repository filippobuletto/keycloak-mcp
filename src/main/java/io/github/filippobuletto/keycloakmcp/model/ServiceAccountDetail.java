package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/**
 * A client's service account user. {@code roles} are mapped directly; {@code effectiveRealmRoles} adds the realm roles
 * that come through groups and composite roles.
 */
public record ServiceAccountDetail(
        String userId,
        String username,
        RoleMappings roles,
        List<String> effectiveRealmRoles,
        List<GroupSummary> groups) {
}
