package io.github.filippobuletto.keycloakmcp.resources;

import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.filippobuletto.keycloakmcp.model.RealmDetail;
import io.github.filippobuletto.keycloakmcp.service.RealmService;
import io.quarkiverse.mcp.server.CompleteResourceTemplate;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ToolCallException;

/** Read-only realm data as MCP resources, as JSON in the same shape as the realm tools. */
@Singleton
public class RealmResources {

    @Inject
    RealmService realmService;

    @Inject
    ObjectMapper mapper;

    @Resource(uri = "keycloak://realms", name = "realms", mimeType = "application/json", description = "Every realm visible to the identity the server acts as (the signed-in user over HTTP, the configured admin over stdio): id, name, display name, enabled flag.")
    String realms() throws JsonProcessingException {
        // A returned List would become one resource content per realm, so encode the array here.
        return mapper.writeValueAsString(keycloak(realmService::list));
    }

    @ResourceTemplate(uriTemplate = "keycloak://realms/{realm}", name = "realm", mimeType = "application/json", description = "A realm's settings: login settings, token lifespans (seconds), password policy and brute-force lockout.")
    RealmDetail realm(String realm) {
        return keycloak(() -> realmService.find(realm))
                .orElseThrow(() -> new McpException("Realm " + realm + " not found", JsonRpcErrorCodes.RESOURCE_NOT_FOUND));
    }

    /** The library answers any exception but McpException with a bare "Internal error"; keep Keycloak's message. */
    private static <T> T keycloak(Supplier<T> call) {
        try {
            return call.get();
        } catch (ToolCallException e) {
            throw new McpException(e.getMessage(), e, JsonRpcErrorCodes.INTERNAL_ERROR);
        }
    }

    @CompleteResourceTemplate("realm")
    List<String> completeRealm(String realm) {
        return realmService.namesStartingWith(realm);
    }
}
