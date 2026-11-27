package io.github.filippobuletto.keycloakmcp.model;

/** A top-level flow's type: BASIC signs users in, CLIENT authenticates clients. */
public enum FlowType {
    BASIC("basic-flow"),
    CLIENT("client-flow");

    private final String providerId;

    FlowType(String providerId) {
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
