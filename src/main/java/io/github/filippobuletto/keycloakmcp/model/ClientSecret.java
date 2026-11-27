package io.github.filippobuletto.keycloakmcp.model;

/** A confidential client's live secret. {@link #toString()} masks it, so an accidental log line prints {@code ***}. */
public record ClientSecret(String realm, String clientId, String secret) {

    @Override
    public String toString() {
        return "ClientSecret[realm=" + realm + ", clientId=" + clientId + ", secret=***]";
    }
}
