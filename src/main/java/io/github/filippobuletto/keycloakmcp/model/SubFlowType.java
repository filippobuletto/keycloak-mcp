package io.github.filippobuletto.keycloakmcp.model;

/** A sub-flow's type: BASIC runs authenticator steps, FORM renders one form and runs form actions. */
public enum SubFlowType {
    BASIC("basic-flow"),
    FORM("form-flow");

    private final String providerId;

    SubFlowType(String providerId) {
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
