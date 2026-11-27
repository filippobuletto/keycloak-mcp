package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HttpAuthTest {

    @Test
    void noTokenIsUnauthorized() {
        McpAssured.newStreamableClient()
                .setExpectConnectFailure(failure -> assertEquals(401, failure.statusCode(), failure.toString()))
                .build()
                .connect();
    }

    @Test
    void invalidTokenIsUnauthorized() {
        McpAssured.newStreamableClient()
                .setBearerToken("not-a-jwt")
                .setExpectConnectFailure(failure -> assertEquals(401, failure.statusCode(), failure.toString()))
                .build()
                .connect();
    }

    @Test
    void tokenWithoutRoleIsForbiddenWithInsufficientScope() {
        McpAssured.newStreamableClient()
                .setBearerToken(McpClients.accessToken(McpClients.GUEST_USER))
                .setExpectConnectFailure(failure -> {
                    assertEquals(403, failure.statusCode(), failure.toString());
                    String challenge = failure.firstHeader("WWW-Authenticate");
                    assertNotNull(challenge, failure.toString());
                    assertTrue(challenge.startsWith("Bearer error=\"insufficient_scope\""), challenge);
                    assertTrue(challenge.contains("scope=\"mcp:tools\""), challenge);
                })
                .build()
                .connect();
    }

    @Test
    void tokenWithRoleCallsTools() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listRealms", Map.of(), response -> assertFalse(response.isError(), response.toString()))
                .thenAssertResults();

        client.disconnect();
    }
}
