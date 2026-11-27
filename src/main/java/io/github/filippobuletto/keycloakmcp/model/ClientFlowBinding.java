package io.github.filippobuletto.keycloakmcp.model;

/** A client's flow override; {@code key} is how Keycloak stores it in authenticationFlowBindingOverrides. */
public enum ClientFlowBinding {
    BROWSER("browser"),
    DIRECT_GRANT("direct_grant");

    private final String key;

    ClientFlowBinding(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
