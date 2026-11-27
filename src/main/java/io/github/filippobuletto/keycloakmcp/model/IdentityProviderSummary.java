package io.github.filippobuletto.keycloakmcp.model;

public record IdentityProviderSummary(
        String alias,
        String displayName,
        String providerId,
        boolean enabled) {
}
