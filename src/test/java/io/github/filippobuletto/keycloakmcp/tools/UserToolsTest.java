package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class UserToolsTest {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url")
    String serverUrl;

    int passwordLogin(String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverUrl + "/realms/acme/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=admin-cli&username=" + username
                        + "&password=" + password))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    String userId(String username) {
        return keycloak.realm("acme").users().searchByUsername(username, true).getFirst().getId();
    }

    String createThrowawayUser() {
        String username = "tmp-" + UUID.randomUUID();
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setEmailVerified(true);
        user.setFirstName("Temp");
        user.setLastName("User");
        user.setEnabled(true);
        try (Response response = keycloak.realm("acme").users().create(user)) {
            assertEquals(201, response.getStatus());
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    boolean userExists(String id) {
        return keycloak.realm("acme").users().searchByUsername("tmp-", false).stream()
                .anyMatch(user -> user.getId().equals(id));
    }

    @AfterEach
    void deleteThrowawayUsers() {
        keycloak.realm("acme").users().searchByUsername("tmp-", false)
                .forEach(user -> keycloak.realm("acme").users().delete(user.getId()).close());
    }

    @Test
    void deleteUserRefusesWithoutConfirm() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id, "confirm", false), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id, "confirm", "true"),
                        response -> assertTrue(response.isError()))
                .thenAssertResults();

        client.disconnect();
        assertTrue(userExists(id));
    }

    @Test
    void deleteUserWithConfirm() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted user " + id + " in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(userExists(id));
    }

    @Test
    void deleteUserUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteUser",
                        Map.of("realm", "acme", "userId", "00000000-0000-0000-0000-000000000000", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("404"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    static List<String> usernames(ToolResponse response) {
        return RealmToolsTest.items(response).stream()
                .map(user -> user.get("username").asText())
                .sorted()
                .toList();
    }

    @Test
    void searchUsersBySubstring() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "search", "corp.com"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals(List.of("asmith", "jdoe"), usernames(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void searchUsersNoMatchReturnsEmptyArray() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "search", "zzz-nobody"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals(List.of(), RealmToolsTest.items(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void searchUsersHonoursMax() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "max", 2), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals(2, usernames(response).size());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void searchUsersPagesWithFirst() {
        McpStreamableTestClient client = McpClients.connected();
        List<List<String>> pages = new ArrayList<>();

        for (int first = 0; first < 3; first++) {
            client.when()
                    .toolsCall("searchUsers", Map.of("realm", "acme", "first", first, "max", 1),
                            response -> pages.add(usernames(response)))
                    .thenAssertResults();
        }

        client.disconnect();
        assertEquals(List.of("asmith", "bwhite", "jdoe"), pages.stream().flatMap(List::stream).sorted().toList());
    }

    @Test
    void searchUsersRejectsNegativeFirst() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "first", -1), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("greater than or equal to 0"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void searchUsersRejectsZeroMax() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("searchUsers", Map.of("realm", "acme", "max", 0), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("greater than or equal to 1"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getUser() {
        String id = userId("jdoe");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getUser", Map.of("realm", "acme", "userId", id), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode user = readTree(text(response));
                    assertEquals(id, user.get("id").asText());
                    assertEquals("jdoe", user.get("username").asText());
                    assertEquals("jdoe@corp.com", user.get("email").asText());
                    assertTrue(user.get("enabled").asBoolean());
                    assertEquals("finance", user.at("/attributes/department/0").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getUserDisabled() {
        String id = userId("bwhite");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getUser", Map.of("realm", "acme", "userId", id), response -> {
                    assertFalse(response.isError(), text(response));
                    assertFalse(readTree(text(response)).get("enabled").asBoolean());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getUserUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getUser", Map.of("realm", "acme", "userId", "00000000-0000-0000-0000-000000000000"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("404"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getUserGroups() {
        String id = userId("bwhite");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getUserGroups", Map.of("realm", "acme", "userId", id), response -> {
                    assertFalse(response.isError(), text(response));
                    List<JsonNode> groups = RealmToolsTest.items(response);
                    assertEquals(1, groups.size(), text(response));
                    assertEquals("/finance/payables", groups.getFirst().get("path").asText());
                    assertEquals("payables", groups.getFirst().get("name").asText());
                    assertTrue(groups.getFirst().get("subGroupCount").isNull(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getUserRoles() {
        String id = userId("asmith");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getUserRoles", Map.of("realm", "acme", "userId", id), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode roles = readTree(text(response));
                    assertTrue(roles.get("realmRoles").toString().contains("\"editor\""), roles.toString());
                    assertEquals("report-reader", roles.at("/clientRoles/reports-service/0").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createUser() {
        String username = "tmp-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createUser", Map.of("realm", "acme", "username", username, "email", username + "@corp.com",
                        "firstName", "Carol", "lastName", "Jones", "attributes", Map.of("site", List.of("milan"))),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode user = readTree(text(response));
                            assertEquals(username, user.get("username").asText());
                            assertTrue(user.get("enabled").asBoolean());
                            assertEquals("milan", user.at("/attributes/site/0").asText());
                            assertEquals(userId(username), user.get("id").asText());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createUserDuplicateUsername() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createUser", Map.of("realm", "acme", "username", "jdoe"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                    assertTrue(text(response).contains("same username"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void userWritesRefuseMasterRealm() {
        String adminId = keycloak.realm("master").users().searchByUsername("admin", true).getFirst().getId();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createUser", Map.of("realm", "master", "username", "tmp-master"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .toolsCall("deleteUser", Map.of("realm", "master", "userId", adminId, "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(keycloak.realm("master").users().searchByUsername("tmp-master", true).isEmpty());
        assertEquals(adminId, keycloak.realm("master").users().get(adminId).toRepresentation().getId());
    }

    @Test
    void updateUserChangesOnlySuppliedFields() {
        String id = createThrowawayUser();
        String email = keycloak.realm("acme").users().get(id).toRepresentation().getEmail();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateUser", Map.of("realm", "acme", "userId", id, "firstName", "Changed"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Changed", readTree(text(response)).get("firstName").asText());
                })
                .thenAssertResults();

        client.disconnect();
        UserRepresentation user = keycloak.realm("acme").users().get(id).toRepresentation();
        assertEquals("Changed", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals(email, user.getEmail());
        assertTrue(user.isEnabled());
    }

    @Test
    void resetPasswordPermanent() throws Exception {
        String id = createThrowawayUser();
        String username = keycloak.realm("acme").users().get(id).toRepresentation().getUsername();
        String password = "Pw-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("resetPassword", Map.of("realm", "acme", "userId", id, "password", password, "temporary", false),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertFalse(text(response).contains(password), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(200, passwordLogin(username, password));
    }

    @Test
    void resetPasswordTemporaryByDefault() {
        String id = createThrowawayUser();
        String password = "Pw-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("resetPassword", Map.of("realm", "acme", "userId", id, "password", password), response -> {
                    assertFalse(response.isError(), text(response));
                    assertFalse(text(response).contains(password), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(keycloak.realm("acme").users().get(id).toRepresentation().getRequiredActions().contains("UPDATE_PASSWORD"));
    }
}
