package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/** Client settings for create or update; a null field is left unchanged. */
public record ClientInput(
        String name,
        Boolean publicClient,
        Boolean serviceAccountsEnabled,
        Boolean standardFlowEnabled,
        Boolean directAccessGrantsEnabled,
        List<String> redirectUris,
        List<String> webOrigins) {
}
