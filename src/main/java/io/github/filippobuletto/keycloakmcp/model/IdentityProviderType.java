package io.github.filippobuletto.keycloakmcp.model;

/** An identity provider type of the server; {@code social} marks the social sign-ins such as google. */
public record IdentityProviderType(
        String id,
        String name,
        boolean social) {
}
