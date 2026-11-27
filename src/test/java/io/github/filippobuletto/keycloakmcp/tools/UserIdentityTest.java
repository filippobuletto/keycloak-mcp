package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.items;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

/** Over HTTP the tools reach Keycloak as the signed-in user, through a token exchanged by keycloak-mcp-server. */
@QuarkusTest
class UserIdentityTest {

    static final String EXCHANGE_CLIENT = "keycloak-mcp-server";
    static final String FORBIDDEN = "Keycloak returned 403 Forbidden";

    @Inject
    Keycloak keycloak;

    RealmEventsConfigRepresentation saved;
    String username;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    RealmResource master() {
        return keycloak.realm("master");
    }

    @BeforeEach
    void recordAdminEvents() {
        saved = acme().getRealmEventsConfig();
        RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
        on.setAdminEventsEnabled(true);
        on.setEnabledEventTypes(null);
        acme().updateRealmEventsConfig(on);
        username = "tmp-uid-" + UUID.randomUUID();
    }

    @AfterEach
    void restore() {
        acme().users().searchByUsername(username, true).forEach(u -> acme().users().delete(u.getId()).close());
        EventToolsTest.restore(acme(), saved);
    }

    @Test
    void adminEventNamesTheSignedInUserAndTheExchangeClient() {
        McpClients.connected().when()
                .toolsCall("createUser", Map.of("realm", "acme", "username", username), response -> {
                    assertFalse(response.isError(), text(response));
                })
                .thenAssertResults();

        String userId = acme().users().searchByUsername(username, true).getFirst().getId();
        List<AdminEventRepresentation> events = acme().getAdminEvents(List.of("CREATE"), null, null, null, null,
                "users/" + userId, null, null, 0, 10);
        assertEquals(1, events.size(), events.toString());
        assertEquals(master().users().searchByUsername(McpClients.ADMIN_USER, true).getFirst().getId(),
                events.getFirst().getAuthDetails().getUserId());
        assertEquals(master().clients().findByClientId(EXCHANGE_CLIENT).getFirst().getId(),
                events.getFirst().getAuthDetails().getClientId());
    }

    @Test
    void viewerReadsUsersOfAcme() {
        McpClients.connected(McpClients.VIEWER_USER).when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "search", "jdoe"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(text(response).contains("\"username\":\"jdoe\""), text(response));
                })
                .thenAssertResults();
    }

    @Test
    void viewerCannotCreateAUser() {
        McpClients.connected(McpClients.VIEWER_USER).when()
                .toolsCall("createUser", Map.of("realm", "acme", "username", username), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).startsWith(FORBIDDEN), text(response));
                })
                .thenAssertResults();

        assertTrue(acme().users().searchByUsername(username, true).isEmpty());
    }

    @Test
    void viewerSeesOnlyAcme() {
        McpClients.connected(McpClients.VIEWER_USER).when()
                .toolsCall("listRealms", Map.of(), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals(List.of("acme"), items(response).stream().map(r -> r.get("name").asText()).toList());
                })
                .thenAssertResults();
    }

    @Test
    void viewerGetsTheSameForbiddenThroughAResource() {
        McpClients.connected(McpClients.VIEWER_USER).when()
                .resourcesRead("keycloak://realms/master")
                .withErrorAssert(error -> assertTrue(error.message().startsWith(FORBIDDEN), error.toString()))
                .send()
                .thenAssertResults();
    }

    @Test
    void failedExchangeAnswersWithItsErrorAndCallsNothing() {
        String token = McpClients.accessToken(McpClients.ADMIN_USER);
        McpStreamableTestClient client = McpAssured.newStreamableClient().setBearerToken(token).build().connect();
        // /mcp checks the signature and expiry only, so the token still opens it; the exchange then fails
        master().users().get(master().users().searchByUsername(McpClients.ADMIN_USER, true).getFirst().getId()).logout();

        client.when()
                .toolsCall("createUser", Map.of("realm", "acme", "username", username), response -> {
                    assertTrue(response.isError());
                    assertEquals("Token exchange at the issuer failed: invalid_request: Invalid token", text(response));
                    assertFalse(text(response).contains(token));
                })
                .thenAssertResults();

        assertTrue(acme().users().searchByUsername(username, true).isEmpty());
        assertEquals(List.of(), acme().getAdminEvents(List.of("CREATE"), null, null, null, null, null, List.of("USER"),
                null, null, 0, 10));
    }
}
