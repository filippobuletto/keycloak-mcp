package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/**
 * A realm role, or a client role when {@code clientId} is set. {@code users} and {@code groups} hold the role directly,
 * one page of each.
 */
public record RoleDetail(
        String id,
        String name,
        String description,
        boolean composite,
        boolean clientRole,
        String clientId,
        List<Composite> composites,
        List<UserSummary> users,
        List<GroupSummary> groups) {

    /** A role a composite role contains; {@code clientId} is null for a realm role. */
    public record Composite(String name, String clientId) {
    }
}
