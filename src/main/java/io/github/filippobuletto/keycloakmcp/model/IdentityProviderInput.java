package io.github.filippobuletto.keycloakmcp.model;

/** Identity provider settings for create or update; a null field is left unchanged. */
public record IdentityProviderInput(
        String displayName,
        Boolean enabled,
        Boolean trustEmail,
        Boolean storeToken,
        Boolean linkOnly,
        Boolean hideOnLogin,
        String firstBrokerLoginFlowAlias,
        String postBrokerLoginFlowAlias) {
}
