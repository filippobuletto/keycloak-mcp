package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.github.filippobuletto.keycloakmcp.OAuthLogin;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Claude Code's OAuth sign-in against Dev Services Keycloak, discovered from the 401 challenge. */
@QuarkusTest
@TestProfile(PublicUrlProfile.class)
class OAuthSignInTest {

    static final URI MCP = URI.create(PublicUrlProfile.PUBLIC_URL);

    @Test
    void metadataAdvertisesTheSignInScope() throws Exception {
        assertEquals("[\"mcp:tools\"]", OAuthLogin.metadata(MCP).path("scopes_supported").toString());
    }

    @Test
    void adminSignsInAndCallsTools() throws Exception {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setBearerToken(OAuthLogin.signIn(MCP, McpClients.ADMIN_USER))
                .build()
                .connect();

        client.when()
                .toolsCall("listRealms", Map.of(), response -> assertFalse(response.isError(), response.toString()))
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void guestSignsInButLacksTheRole() throws Exception {
        String token = OAuthLogin.signIn(MCP, McpClients.GUEST_USER);

        McpAssured.newStreamableClient()
                .setBearerToken(token)
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
}
