package io.github.filippobuletto.keycloakmcp.model;

/** {@code realmDefault} is what new clients of the realm get: {@code default}, {@code optional} or {@code none}. */
public record ClientScopeSummary(
        String id,
        String name,
        String description,
        String protocol,
        String realmDefault) {
}
