package io.github.filippobuletto.keycloakmcp.model;

/**
 * When an identity provider mapper applies, as Keycloak's {@code IdentityProviderMapperSyncMode}: {@code INHERIT} uses
 * the provider's sync mode, {@code IMPORT} applies on the first sign-in only, {@code FORCE} on every sign-in.
 * Keycloak runs a mapper without a sync mode as {@code LEGACY}.
 */
public enum SyncMode {
    INHERIT,
    LEGACY,
    IMPORT,
    FORCE;

    /** The config key Keycloak stores the sync mode under. */
    public static final String CONFIG_KEY = "syncMode";
}
