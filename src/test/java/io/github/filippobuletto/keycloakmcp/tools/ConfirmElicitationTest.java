package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonObject;

/**
 * With {@code MCP_CONFIRM_MODE=elicit}: a confirmation is a form elicitation the server sends to the user, and the
 * tool runs only when the user accepts with the box ticked.
 */
@QuarkusTest
@TestProfile(ElicitProfile.class)
class ConfirmElicitationTest {

    @Inject
    Keycloak keycloak;

    /** Connected client acting as {@code mcp-user} that declares form-mode elicitation. */
    static McpStreamableTestClient elicitingClient() {
        return McpAssured.newStreamableClient()
                .setBearerToken(McpClients.accessToken(McpClients.ADMIN_USER))
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();
    }

    String createThrowawayUser() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("tmp-" + UUID.randomUUID());
        user.setEnabled(true);
        keycloak.realm("acme").users().create(user).close();
        return keycloak.realm("acme").users().searchByUsername(user.getUsername(), true).getFirst().getId();
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

    /** The {@code elicitation/create} request the server sent during the pending call. */
    static JsonObject awaitElicitation(McpStreamableTestClient client) {
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals(1, requests.size(), requests.toString());
        JsonObject request = requests.getFirst();
        assertEquals("elicitation/create", request.getString("method"), request.encode());
        return request;
    }

    /** Answers a server request with an elicitation result. */
    static void answer(McpStreamableTestClient client, JsonObject request, String action, Boolean confirm) {
        JsonObject result = new JsonObject().put("action", action);
        if (confirm != null) {
            result.put("content", new JsonObject().put("confirm", confirm));
        }
        client.sendAndForget(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", request.getValue("id"))
                .put("result", result));
    }

    /**
     * Calls a tool, answers its dialog with {@code action} and {@code confirm}, checks the tool response, and returns
     * the dialog message.
     */
    static String callAnswering(String tool, Map<String, Object> args, String action, Boolean confirm,
            Consumer<ToolResponse> check) {
        McpStreamableTestClient client = elicitingClient();
        var pending = client.when().toolsCall(tool, args, check);
        JsonObject request = awaitElicitation(client);
        answer(client, request, action, confirm);
        pending.thenAssertResults();
        client.disconnect();
        return request.getJsonObject("params").getString("message");
    }

    /** Calls deleteUser, answers its dialog with {@code action} and {@code confirm}, and checks the tool response. */
    void deleteUserAnswering(String id, String action, Boolean confirm, Consumer<ToolResponse> check) {
        callAnswering("deleteUser", Map.of("realm", "acme", "userId", id), action, confirm, check);
    }

    /** Calls a tool, declines its dialog, checks the refusal, and checks that the message names each of {@code named}. */
    static void declines(String tool, Map<String, Object> args, String... named) {
        String message = callAnswering(tool, args, "decline", null, response -> {
            assertTrue(response.isError(), text(response));
            assertTrue(text(response).contains(tool + " was not confirmed: the user declined"), text(response));
        });
        for (String name : named) {
            assertTrue(message.contains(name), name + " missing in: " + message);
        }
    }

    /** Calls a tool, accepts its dialog with the box ticked, and checks that the tool succeeded. */
    static void accepts(String tool, Map<String, Object> args) {
        callAnswering(tool, args, "accept", true, response -> assertFalse(response.isError(), text(response)));
    }

    @Test
    void deleteUserAsksWithACheckbox() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = elicitingClient();
        var pending = client.when()
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id), response -> {
                });
        JsonObject params = awaitElicitation(client).getJsonObject("params");
        answer(client, client.snapshot().requests().getFirst(), "decline", null);
        pending.thenAssertResults();
        client.disconnect();

        String message = params.getString("message");
        assertTrue(message.contains(id) && message.contains("acme"), message);
        JsonObject schema = params.getJsonObject("requestedSchema");
        assertEquals(List.of("confirm"), List.copyOf(schema.getJsonObject("properties").fieldNames()), schema.encode());
        assertEquals("boolean", schema.getJsonObject("properties").getJsonObject("confirm").getString("type"));
        assertEquals(List.of("confirm"), schema.getJsonArray("required").getList(), schema.encode());
    }

    @Test
    void deleteUserRunsOnAcceptWithTheBoxTicked() {
        String id = createThrowawayUser();

        deleteUserAnswering(id, "accept", true, response -> {
            assertFalse(response.isError(), text(response));
            assertEquals("Deleted user " + id + " in realm acme", text(response));
        });

        assertFalse(userExists(id));
    }

    @Test
    void deleteUserRefusesADecline() {
        String id = createThrowawayUser();

        deleteUserAnswering(id, "decline", null, response -> {
            assertTrue(response.isError());
            assertTrue(text(response).contains("declined"), text(response));
            assertTrue(text(response).endsWith("Nothing was changed."), text(response));
        });

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserRefusesAnUntickedBox() {
        String id = createThrowawayUser();

        deleteUserAnswering(id, "accept", false, response -> {
            assertTrue(response.isError());
            assertTrue(text(response).contains("declined"), text(response));
        });

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserRefusesACancel() {
        String id = createThrowawayUser();

        deleteUserAnswering(id, "cancel", null, response -> {
            assertTrue(response.isError());
            assertTrue(text(response).contains("dismissed"), text(response));
            assertTrue(text(response).endsWith("Nothing was changed."), text(response));
        });

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserRefusesWhenNobodyAnswers() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = elicitingClient();

        var pending = client.when()
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("no answer within 2 s"), text(response));
                    assertTrue(text(response).endsWith("Nothing was changed."), text(response));
                });
        awaitElicitation(client);
        pending.thenAssertResults();
        client.disconnect();

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserRefusesAClientWithoutElicitation() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteUser", Map.of("realm", "acme", "userId", id, "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("MCP_CONFIRM_MODE=flag"), text(response));
                    assertTrue(text(response).endsWith("Nothing was changed."), text(response));
                })
                .thenAssertResults();
        client.disconnect();

        assertTrue(userExists(id));
    }

    /** Client on the stateless revision 2026-07-28 that declares form-mode elicitation. */
    static McpStreamableTestClient statelessClient() {
        return McpAssured.newStreamableClient()
                .setBearerToken(McpClients.accessToken(McpClients.ADMIN_USER))
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .setStateless()
                .build()
                .connect();
    }

    static JsonObject elicitationAnswer(String action, Boolean confirm) {
        JsonObject answer = new JsonObject().put("action", action);
        if (confirm != null) {
            answer.put("content", new JsonObject().put("confirm", confirm));
        }
        return new JsonObject().put("confirm", answer);
    }

    @Test
    void deleteUserStatelessAsksThroughInputRequired() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = statelessClient();

        client.when()
                .toolsCall("deleteUser")
                .withArguments(Map.of("realm", "acme", "userId", id))
                .withRawAssert(raw -> {
                    JsonObject result = raw.getJsonObject("result");
                    assertEquals("input_required", result.getString("resultType"), raw.encode());
                    JsonObject request = result.getJsonObject("inputRequests").getJsonObject("confirm");
                    assertEquals("elicitation/create", request.getString("method"), raw.encode());
                    String message = request.getJsonObject("params").getString("message");
                    assertTrue(message.contains(id) && message.contains("acme"), message);
                })
                .send()
                .thenAssertResults();
        client.disconnect();

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserStatelessRunsOnAnAcceptedRetry() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = statelessClient();

        client.when()
                .toolsCall("deleteUser")
                .withArguments(Map.of("realm", "acme", "userId", id))
                .withInputResponses(elicitationAnswer("accept", true))
                .withAssert(response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted user " + id + " in realm acme", text(response));
                })
                .send()
                .thenAssertResults();
        client.disconnect();

        assertFalse(userExists(id));
    }

    @Test
    void deleteUserStatelessRefusesADeclinedRetry() {
        String id = createThrowawayUser();
        McpStreamableTestClient client = statelessClient();

        client.when()
                .toolsCall("deleteUser")
                .withArguments(Map.of("realm", "acme", "userId", id))
                .withInputResponses(elicitationAnswer("decline", null))
                .withAssert(response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("declined"), text(response));
                })
                .send()
                .thenAssertResults();
        client.disconnect();

        assertTrue(userExists(id));
    }

    @Test
    void deleteUserOnMasterFailsBeforeAnyDialog() {
        String adminId = keycloak.realm("master").users().searchByUsername("admin", true).getFirst().getId();
        McpStreamableTestClient client = elicitingClient();

        client.when()
                .toolsCall("deleteUser", Map.of("realm", "master", "userId", adminId), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        assertEquals(List.of(), client.snapshot().requests());
        client.disconnect();
    }
}
