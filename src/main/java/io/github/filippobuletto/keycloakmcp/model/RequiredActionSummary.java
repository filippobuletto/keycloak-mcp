package io.github.filippobuletto.keycloakmcp.model;

/**
 * A registered required action. {@code defaultAction} means every new user of the realm gets it; a disabled action is
 * never asked for, even when a user has it pending.
 */
public record RequiredActionSummary(
        String alias,
        String name,
        boolean enabled,
        boolean defaultAction) {
}
