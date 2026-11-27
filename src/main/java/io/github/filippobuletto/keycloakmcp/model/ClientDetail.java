package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/**
 * Client settings without credentials; only known-safe attributes are included. {@code protocolMappers} are the
 * client's own mappers and {@code scopeMappings} the roles its dedicated scope maps.
 */
public record ClientDetail(
        String id,
        String clientId,
        String name,
        String description,
        boolean enabled,
        String protocol,
        boolean publicClient,
        boolean serviceAccountsEnabled,
        boolean standardFlowEnabled,
        boolean directAccessGrantsEnabled,
        boolean implicitFlowEnabled,
        boolean fullScopeAllowed,
        String clientAuthenticatorType,
        String rootUrl,
        String baseUrl,
        List<String> redirectUris,
        List<String> webOrigins,
        Map<String, String> attributes,
        List<ClientScopeDetail.Mapper> protocolMappers,
        RoleMappings scopeMappings) {
}
