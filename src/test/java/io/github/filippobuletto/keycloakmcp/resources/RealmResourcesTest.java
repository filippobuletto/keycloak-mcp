package io.github.filippobuletto.keycloakmcp.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RealmResourcesTest {

    static final String TEMPLATE = "keycloak://realms/{realm}";

    @Test
    void realmListResource() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .resourcesList(page -> {
                    var realms = page.resources().stream().filter(r -> r.uri().equals("keycloak://realms")).toList();
                    assertEquals(1, realms.size(), page.resources().toString());
                    assertEquals("application/json", realms.getFirst().mimeType());
                })
                .resourcesRead("keycloak://realms", response -> {
                    TextResourceContents contents = single(response);
                    assertEquals("keycloak://realms", contents.uri());
                    assertEquals("application/json", contents.mimeType());
                    List<String> names = StreamSupport.stream(readTree(contents.text()).spliterator(), false)
                            .map(realm -> realm.get("name").asText())
                            .toList();
                    assertTrue(names.containsAll(List.of("acme", "master")), names.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void realmTemplate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .resourcesTemplatesList(page -> {
                    var templates = page.templates().stream().filter(t -> t.uriTemplate().equals(TEMPLATE)).toList();
                    assertEquals(1, templates.size(), page.templates().toString());
                    assertEquals("application/json", templates.getFirst().mimeType());
                })
                .resourcesRead("keycloak://realms/acme", response -> {
                    TextResourceContents contents = single(response);
                    assertEquals("keycloak://realms/acme", contents.uri());
                    assertEquals("application/json", contents.mimeType());
                    JsonNode realm = readTree(contents.text());
                    assertEquals("acme", realm.get("name").asText());
                    assertEquals(600, realm.at("/tokens/accessTokenLifespan").asInt());
                    assertTrue(realm.has("passwordPolicy"), realm.toString());
                    assertFalse(realm.at("/bruteForce/enabled").asBoolean(), realm.toString());
                })
                .resourcesRead("keycloak://realms/master", response -> {
                    assertEquals("master", readTree(single(response).text()).get("name").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void realmTemplateUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .resourcesRead("keycloak://realms/nope")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, error.code(), error.toString());
                    assertEquals("Realm nope not found", error.message());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void realmTemplateCompletesRealmNames() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .resourceTemplateComplete(TEMPLATE, "realm", "ac", completion -> {
                    assertEquals(List.of("acme"), completion.values());
                })
                .resourceTemplateComplete(TEMPLATE, "realm", "m", completion -> {
                    assertTrue(completion.values().contains("master"), completion.values().toString());
                    assertFalse(completion.values().contains("acme"), completion.values().toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    static TextResourceContents single(ResourceResponse response) {
        assertEquals(1, response.contents().size(), response.contents().toString());
        return response.contents().getFirst().asText();
    }

    static JsonNode readTree(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new AssertionError("not JSON: " + json, e);
        }
    }
}
