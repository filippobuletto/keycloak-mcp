package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/** {@code clientRoles} maps a clientId to the names of that client's roles mapped to the scope. */
public record ClientScopeDetail(
        String id,
        String name,
        String description,
        String protocol,
        boolean includeInTokenScope,
        boolean displayOnConsentScreen,
        String consentScreenText,
        String guiOrder,
        List<Mapper> mappers,
        List<String> realmRoles,
        Map<String, List<String>> clientRoles) {

    /** {@code type} is the protocol mapper type id, for example {@code oidc-usermodel-attribute-mapper}. */
    public record Mapper(String name, String type, Map<String, String> config) {
    }
}
