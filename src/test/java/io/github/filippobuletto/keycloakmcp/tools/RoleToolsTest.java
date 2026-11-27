package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RoleToolsTest {

    @Inject
    Keycloak keycloak;

    String jdoeId() {
        return keycloak.realm("acme").users().searchByUsername("jdoe", true).getFirst().getId();
    }

    String createThrowawayRole() {
        RoleRepresentation role = new RoleRepresentation();
        role.setName("tmp-" + UUID.randomUUID());
        keycloak.realm("acme").roles().create(role);
        return role.getName();
    }

    boolean roleExists(String name) {
        return keycloak.realm("acme").roles().list().stream().anyMatch(role -> role.getName().equals(name));
    }

    @AfterEach
    void deleteThrowawayRoles() {
        keycloak.realm("acme").roles().list().stream()
                .filter(role -> role.getName().startsWith("tmp-"))
                .forEach(role -> keycloak.realm("acme").roles().deleteRole(role.getName()));
    }

    @Test
    void listRealmRoles() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listRealmRoles", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    String json = text(response);
                    assertTrue(json.contains("\"name\":\"viewer\""), json);
                    assertTrue(json.contains("\"name\":\"editor\""), json);
                    assertTrue(json.contains("\"description\":\"Read-only access to ACME applications\""), json);
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listRealmRolesUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listRealmRoles", Map.of("realm", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listClientRoles() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClientRoles", Map.of("realm", "acme", "clientId", "reports-service"), response -> {
                    assertFalse(response.isError(), text(response));
                    List<JsonNode> listed = RealmToolsTest.items(response);
                    assertEquals(1, listed.size(), text(response));
                    JsonNode role = listed.getFirst();
                    assertEquals("report-reader", role.get("name").asText());
                    assertTrue(role.get("clientRole").asBoolean());
                    assertFalse(role.get("composite").asBoolean());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listClientRolesUnknownClient() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClientRoles", Map.of("realm", "acme", "clientId", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createRealmRole() {
        String name = "tmp-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealmRole", Map.of("realm", "acme", "name", name, "description", "Temporary"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode role = readTree(text(response));
                    assertEquals(name, role.get("name").asText());
                    assertEquals("Temporary", role.get("description").asText());
                    assertFalse(role.get("clientRole").asBoolean());
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(roleExists(name));
    }

    @Test
    void createRealmRoleDuplicate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealmRole", Map.of("realm", "acme", "name", "viewer"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void assignThenUnassignRealmRole() {
        String role = createThrowawayRole();
        String userId = jdoeId();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("assignRealmRole", Map.of("realm", "acme", "userId", userId, "role", role),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("getUserRoles", Map.of("realm", "acme", "userId", userId),
                        response -> assertTrue(text(response).contains("\"" + role + "\""), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("unassignRealmRole", Map.of("realm", "acme", "userId", userId, "role", role),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("getUserRoles", Map.of("realm", "acme", "userId", userId),
                        response -> assertFalse(text(response).contains("\"" + role + "\""), text(response)))
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void assignUnknownRealmRole() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("assignRealmRole", Map.of("realm", "acme", "userId", jdoeId(), "role", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void roleWritesRefuseMasterRealm() {
        String adminId = keycloak.realm("master").users().searchByUsername("admin", true).getFirst().getId();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealmRole", Map.of("realm", "master", "name", "tmp-master"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .toolsCall("unassignRealmRole", Map.of("realm", "master", "userId", adminId, "role", "admin"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(keycloak.realm("master").users().get(adminId).roles().realmLevel().listAll().stream()
                .anyMatch(role -> role.getName().equals("admin")));
    }

    @Test
    void deleteRealmRoleRefusesWithoutConfirm() {
        String role = createThrowawayRole();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRealmRole", Map.of("realm", "acme", "name", role, "confirm", false), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(roleExists(role));
    }

    @Test
    void deleteRealmRoleWithConfirm() {
        String role = createThrowawayRole();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRealmRole", Map.of("realm", "acme", "name", role, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted realm role " + role + " in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(roleExists(role));
    }

    @Test
    void getRealmRoleShowsDirectUsersAndGroups() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "acme", "name", "viewer"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode role = readTree(text(response));
                    assertEquals("viewer", role.get("name").asText());
                    assertFalse(role.get("composite").asBoolean());
                    assertTrue(role.get("clientId").isNull(), role.toString());
                    assertEquals(List.of("jdoe"), names(role.get("users"), "username"), role.toString());
                    assertEquals(List.of("/finance"), names(role.get("groups"), "path"), role.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmRoleShowsCompositesWithClientIds() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "acme", "name", "default-roles-acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode role = readTree(text(response));
                    assertTrue(role.get("composite").asBoolean());
                    List<String> composites = new ArrayList<>();
                    role.get("composites").forEach(c -> composites
                            .add((c.get("clientId").isNull() ? "" : c.get("clientId").asText() + ":")
                                    + c.get("name").asText()));
                    assertTrue(composites.containsAll(List.of("offline_access", "account:manage-account")),
                            composites.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmRolePagesHolders() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "acme", "name", "default-roles-acme", "first", 0, "max", 1),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals(1, readTree(text(response)).get("users").size(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientRole() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "acme", "clientId", "account", "name", "manage-account"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode role = readTree(text(response));
                            assertEquals("manage-account", role.get("name").asText());
                            assertTrue(role.get("clientRole").asBoolean());
                            assertEquals("account", role.get("clientId").asText());
                            assertEquals(List.of("manage-account-links"), names(role.get("composites"), "name"),
                                    role.toString());
                            assertEquals("account", role.at("/composites/0/clientId").asText(), role.toString());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmRoleUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "acme", "name", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Role nope not found in realm acme", text(response));
                })
                .toolsCall("getRealmRole", Map.of("realm", "acme", "clientId", "account", "name", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Role nope of client account not found in realm acme", text(response));
                })
                .toolsCall("getRealmRole", Map.of("realm", "nope", "name", "viewer"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmRoleWorksOnMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealmRole", Map.of("realm", "master", "name", "admin"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(readTree(text(response)).get("composite").asBoolean(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    static List<String> names(JsonNode array, String field) {
        List<String> names = new ArrayList<>();
        array.forEach(item -> names.add(item.get(field).asText()));
        return names;
    }
}
