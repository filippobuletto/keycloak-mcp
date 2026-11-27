package io.github.filippobuletto.keycloakmcp.model;

/** Client scope settings for create or update; a null field is left unchanged. */
public record ClientScopeInput(
        String description,
        Boolean includeInTokenScope,
        Boolean displayOnConsentScreen,
        String consentScreenText) {
}
