package io.github.filippobuletto.keycloakmcp.model;

public record UserSummary(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled) {
}
