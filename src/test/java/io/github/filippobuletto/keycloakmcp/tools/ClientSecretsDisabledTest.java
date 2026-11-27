package io.github.filippobuletto.keycloakmcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

/** Without {@code MCP_CLIENT_SECRETS_ENABLED}, the secret tools neither show up nor run. */
@QuarkusTest
class ClientSecretsDisabledTest {

    @Test
    void secretToolsAreNotListed() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsList(page -> {
                    List<String> names = page.tools().stream().map(tool -> tool.name()).toList();
                    assertTrue(names.contains("getClient"), names.toString());
                    assertFalse(names.contains("getClientSecret"), names.toString());
                    assertFalse(names.contains("regenerateClientSecret"), names.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void secretToolsCannotBeCalled() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientSecret")
                .withArguments(Map.of("realm", "acme", "clientId", "reports-service", "confirm", true))
                .withErrorAssert(error -> assertTrue(error.message().contains("getClientSecret"), error.message()))
                .send()
                .toolsCall("regenerateClientSecret")
                .withArguments(Map.of("realm", "acme", "clientId", "reports-service", "confirm", true))
                .withErrorAssert(
                        error -> assertTrue(error.message().contains("regenerateClientSecret"), error.message()))
                .send()
                .thenAssertResults();

        client.disconnect();
    }
}
