package io.github.filippobuletto.keycloakmcp.model;

/** The number of active (regular) and offline sessions of one client. */
public record SessionStat(
        String clientId,
        long active,
        long offline) {
}
