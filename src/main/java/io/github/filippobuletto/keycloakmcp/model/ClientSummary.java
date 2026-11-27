package io.github.filippobuletto.keycloakmcp.model;

public record ClientSummary(
        String id,
        String clientId,
        String name,
        boolean enabled,
        String protocol,
        boolean publicClient,
        boolean serviceAccountsEnabled) {
}
