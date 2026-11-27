package io.github.filippobuletto.keycloakmcp.model;

/** A regular session ends at logout or idle timeout; an offline session backs an offline token and outlives both. */
public enum SessionType {
    REGULAR,
    OFFLINE
}
