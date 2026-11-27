package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GroupToolsTest {

    @Inject
    Keycloak keycloak;

    String jdoeId() {
        return keycloak.realm("acme").users().searchByUsername("jdoe", true).getFirst().getId();
    }

    String createThrowawayGroup() {
        GroupRepresentation group = new GroupRepresentation();
        group.setName("tmp-" + UUID.randomUUID());
        keycloak.realm("acme").groups().add(group).close();
        return "/" + group.getName();
    }

    boolean groupExists(String path) {
        return keycloak.realm("acme").groups().groups(null, 0, 1000, true).stream()
                .anyMatch(group -> group.getPath().equals(path));
    }

    @AfterEach
    void deleteThrowawayGroups() {
        keycloak.realm("acme").groups().groups(null, 0, 1000, true).stream()
                .filter(group -> group.getName().startsWith("tmp-"))
                .forEach(group -> keycloak.realm("acme").groups().group(group.getId()).remove());
    }

    static Map<String, JsonNode> byPath(ToolResponse response) {
        return RealmToolsTest.items(response).stream()
                .collect(Collectors.toMap(group -> group.get("path").asText(), group -> group));
    }

    static List<String> usernames(ToolResponse response) {
        return RealmToolsTest.items(response).stream().map(user -> user.get("username").asText()).toList();
    }

    @Test
    void listGroupsFlattensTree() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listGroups", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    Map<String, JsonNode> groups = byPath(response);
                    assertEquals(List.of("/engineering", "/finance", "/finance/payables"),
                            groups.keySet().stream().sorted().toList());
                    assertEquals(1, groups.get("/finance").get("subGroupCount").asInt());
                    assertEquals("payables", groups.get("/finance/payables").get("name").asText());
                    assertEquals(0, groups.get("/finance/payables").get("subGroupCount").asInt());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listGroupsUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listGroups", Map.of("realm", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getGroupByPath() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getGroup", Map.of("realm", "acme", "path", "/finance"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode group = readTree(text(response));
                    assertEquals("finance", group.get("name").asText());
                    assertEquals(1, group.get("subGroupCount").asInt());
                    assertEquals("FIN-01", group.at("/attributes/cost-center/0").asText());
                    assertEquals("viewer", group.at("/realmRoles/0").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getGroupUnknownPath() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getGroup", Map.of("realm", "acme", "path", "/nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getGroupMembers() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getGroupMembers", Map.of("realm", "acme", "path", "/finance/payables"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals(List.of("bwhite"), usernames(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getGroupMembersPagesWithFirst() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getGroupMembers", Map.of("realm", "acme", "path", "/finance/payables", "first", 1),
                        response -> assertEquals(List.of(), usernames(response)))
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getGroupMembersRejectsZeroMax() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getGroupMembers", Map.of("realm", "acme", "path", "/finance", "max", 0), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("greater than or equal to 1"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createTopLevelAndNestedGroup() {
        String parent = "tmp-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createGroup", Map.of("realm", "acme", "name", parent), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("/" + parent, readTree(text(response)).get("path").asText());
                })
                .thenAssertResults();
        client.when()
                .toolsCall("createGroup", Map.of("realm", "acme", "name", "child", "parentPath", "/" + parent), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("/" + parent + "/child", readTree(text(response)).get("path").asText());
                })
                .thenAssertResults();
        client.when()
                .toolsCall("listGroups", Map.of("realm", "acme"), response -> {
                    Map<String, JsonNode> groups = byPath(response);
                    assertEquals(1, groups.get("/" + parent).get("subGroupCount").asInt());
                    assertTrue(groups.containsKey("/" + parent + "/child"), groups.keySet().toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createGroupDuplicate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createGroup", Map.of("realm", "acme", "name", "finance"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createGroupUnknownParent() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createGroup", Map.of("realm", "acme", "name", "tmp-orphan", "parentPath", "/nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(groupExists("/tmp-orphan"));
    }

    @Test
    void addThenRemoveUserFromGroup() {
        String path = createThrowawayGroup();
        String userId = jdoeId();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addUserToGroup", Map.of("realm", "acme", "userId", userId, "path", path),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("getGroupMembers", Map.of("realm", "acme", "path", path),
                        response -> assertEquals(List.of("jdoe"), usernames(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("getUserGroups", Map.of("realm", "acme", "userId", userId),
                        response -> assertTrue(text(response).contains(path), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("removeUserFromGroup", Map.of("realm", "acme", "userId", userId, "path", path),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("getGroupMembers", Map.of("realm", "acme", "path", path),
                        response -> assertEquals(List.of(), usernames(response)))
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void groupWritesRefuseMasterRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createGroup", Map.of("realm", "master", "name", "tmp-master"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(keycloak.realm("master").groups().groups("tmp-master", 0, 10, true).isEmpty());
    }

    @Test
    void deleteGroupRefusesWithoutConfirm() {
        String path = createThrowawayGroup();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteGroup", Map.of("realm", "acme", "path", path), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(groupExists(path));
    }

    @Test
    void deleteGroupWithConfirm() {
        String path = createThrowawayGroup();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteGroup", Map.of("realm", "acme", "path", path, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted group " + path + " in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(groupExists(path));
    }
}
