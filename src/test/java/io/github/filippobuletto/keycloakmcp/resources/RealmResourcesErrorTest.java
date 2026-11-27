package io.github.filippobuletto.keycloakmcp.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.filippobuletto.keycloakmcp.model.RealmDetail;
import io.github.filippobuletto.keycloakmcp.model.RealmSummary;
import io.github.filippobuletto.keycloakmcp.service.RealmService;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolCallException;

/** Keycloak failures other than an unknown realm keep their message on a resource read. */
class RealmResourcesErrorTest {

    static final String DOWN = "Keycloak is unreachable: Connection refused";

    static RealmResources failing() {
        RealmResources resources = new RealmResources();
        resources.mapper = new ObjectMapper();
        resources.realmService = new RealmService() {
            @Override
            public List<RealmSummary> list() {
                throw new ToolCallException(DOWN);
            }

            @Override
            public Optional<RealmDetail> find(String realm) {
                throw new ToolCallException(DOWN);
            }
        };
        return resources;
    }

    @Test
    void realmListKeepsKeycloakMessage() {
        McpException error = assertThrows(McpException.class, () -> failing().realms());
        assertEquals(DOWN, error.getMessage());
        assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.getJsonRpcErrorCode());
    }

    @Test
    void realmTemplateKeepsKeycloakMessage() {
        McpException error = assertThrows(McpException.class, () -> failing().realm("acme"));
        assertEquals(DOWN, error.getMessage());
        assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.getJsonRpcErrorCode());
    }
}
