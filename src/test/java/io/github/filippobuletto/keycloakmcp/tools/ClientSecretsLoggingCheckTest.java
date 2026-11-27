package io.github.filippobuletto.keycloakmcp.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClientSecretsLoggingCheckTest {

    @Test
    void secretsOffStartsWithAnyLogging() {
        assertDoesNotThrow(() -> new ClientSecretsLoggingCheck(false, true, Optional.of("request-response")));
    }

    @Test
    void secretsOnWithoutLoggingStarts() {
        assertDoesNotThrow(() -> new ClientSecretsLoggingCheck(true, false, Optional.empty()));
    }

    @Test
    void restClientScopeNoneStarts() {
        assertDoesNotThrow(() -> new ClientSecretsLoggingCheck(true, false, Optional.of("NONE")));
    }

    @Test
    void trafficLoggingIsRefused() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ClientSecretsLoggingCheck(true, true, Optional.empty()));
        assertTrue(error.getMessage().startsWith("MCP_CLIENT_SECRETS_ENABLED=true cannot run with"
                + " quarkus.mcp.server.traffic-logging.enabled=true"), error.getMessage());
    }

    @Test
    void restClientLoggingIsRefused() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ClientSecretsLoggingCheck(true, false, Optional.of("request-response")));
        assertTrue(error.getMessage().contains("quarkus.rest-client.logging.scope=request-response"),
                error.getMessage());
    }
}
