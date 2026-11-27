package io.github.filippobuletto.keycloakmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RealmToolsTest {

    @Inject
    Keycloak keycloak;

    boolean realmExists(String name) {
        return keycloak.realms().findAll().stream().anyMatch(realm -> realm.getRealm().equals(name));
    }

    String createThrowawayRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("tmp-" + UUID.randomUUID());
        realm.setEnabled(true);
        keycloak.realms().create(realm);
        return realm.getRealm();
    }

    @AfterEach
    void deleteThrowawayRealms() {
        keycloak.realms().findAll().stream()
                .filter(realm -> realm.getRealm().startsWith("tmp-"))
                .forEach(realm -> keycloak.realm(realm.getRealm()).remove());
    }

    /** List tools answer with one text block holding a JSON array, empty when nothing matches. */
    static List<JsonNode> items(ToolResponse response) {
        assertEquals(1, response.content().size(), "expected one JSON array content block");
        JsonNode array = readTree(response.content().getFirst().asText().text());
        assertTrue(array.isArray(), array.toString());
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    static String text(ToolResponse response) {
        return response.content().stream()
                .map(c -> c.asText().text())
                .collect(Collectors.joining("\n"));
    }

    @Test
    void listRealms() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listRealms", Map.of(), response -> {
                    assertFalse(response.isError());
                    List<String> names = items(response).stream().map(realm -> realm.get("name").asText()).toList();
                    assertTrue(names.containsAll(List.of("master", "acme")), names.toString());
                    assertTrue(text(response).contains("\"displayName\":\"ACME\""), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealm", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode realm = readTree(text(response));
                    assertEquals("acme", realm.get("name").asText());
                    assertEquals("ACME", realm.get("displayName").asText());
                    assertTrue(realm.get("enabled").asBoolean());
                    assertTrue(realm.at("/login/resetPasswordAllowed").asBoolean());
                    assertTrue(realm.at("/login/loginWithEmailAllowed").asBoolean());
                    assertFalse(realm.at("/login/registrationAllowed").asBoolean());
                    assertEquals(600, realm.at("/tokens/accessTokenLifespan").asInt());
                    assertEquals(3600, realm.at("/tokens/ssoSessionIdleTimeout").asInt());
                    assertEquals(43200, realm.at("/tokens/ssoSessionMaxLifespan").asInt());
                    assertTrue(realm.has("passwordPolicy"), realm.toString());
                    assertTrue(realm.get("passwordPolicy").isNull(), realm.toString());
                    assertFalse(realm.at("/bruteForce/enabled").asBoolean(), realm.toString());
                    assertEquals("MULTIPLE", realm.at("/bruteForce/strategy").asText());
                    assertEquals(30, realm.at("/bruteForce/failureFactor").asInt());
                    assertFalse(realm.at("/tokens/revokeRefreshToken").asBoolean(), realm.toString());
                    assertEquals(0, realm.at("/tokens/refreshTokenMaxReuse").asInt(), realm.toString());
                    assertEquals(0, realm.at("/tokens/clientSessionIdleTimeout").asInt(), realm.toString());
                    assertEquals(0, realm.at("/tokens/clientSessionMaxLifespan").asInt(), realm.toString());
                    assertFalse(realm.at("/tokens/offlineSessionMaxLifespanEnabled").asBoolean(), realm.toString());
                    assertTrue(realm.has("adminEventsExpiration"), realm.toString());
                    assertTrue(realm.get("adminEventsExpiration").isNull(), realm.toString());
                    assertEquals("ENABLED", realm.get("unmanagedAttributePolicy").asText(), realm.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmShowsRefreshTokenSessionAndAdminEventSettings() {
        String name = createThrowawayRealm();
        RealmRepresentation settings = keycloak.realm(name).toRepresentation();
        settings.setRevokeRefreshToken(true);
        settings.setRefreshTokenMaxReuse(2);
        settings.setClientSessionIdleTimeout(900);
        settings.setClientSessionMaxLifespan(7200);
        settings.setOfflineSessionMaxLifespanEnabled(true);
        settings.setOfflineSessionMaxLifespan(86400);
        settings.getAttributes().put("adminEventsExpiration", "3600");
        keycloak.realm(name).update(settings);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealm", Map.of("realm", name), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode realm = readTree(text(response));
                    JsonNode tokens = realm.get("tokens");
                    assertTrue(tokens.get("revokeRefreshToken").asBoolean(), tokens.toString());
                    assertEquals(2, tokens.get("refreshTokenMaxReuse").asInt());
                    assertEquals(900, tokens.get("clientSessionIdleTimeout").asInt());
                    assertEquals(7200, tokens.get("clientSessionMaxLifespan").asInt());
                    assertTrue(tokens.get("offlineSessionMaxLifespanEnabled").asBoolean());
                    assertEquals(86400, tokens.get("offlineSessionMaxLifespan").asInt());
                    assertEquals(3600, realm.get("adminEventsExpiration").asLong(), realm.toString());
                    assertTrue(realm.get("unmanagedAttributePolicy").isNull(), realm.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmShowsPasswordPolicyAndBruteForce() {
        String name = createThrowawayRealm();
        RealmRepresentation settings = keycloak.realm(name).toRepresentation();
        settings.setPasswordPolicy("length(12) and notUsername");
        settings.setBruteForceProtected(true);
        settings.setBruteForceStrategy(RealmRepresentation.BruteForceStrategy.LINEAR);
        settings.setPermanentLockout(false);
        settings.setMaxTemporaryLockouts(3);
        settings.setFailureFactor(5);
        settings.setWaitIncrementSeconds(120);
        settings.setMaxFailureWaitSeconds(1800);
        settings.setMaxDeltaTimeSeconds(3600);
        settings.setMinimumQuickLoginWaitSeconds(30);
        settings.setQuickLoginCheckMilliSeconds(500L);
        keycloak.realm(name).update(settings);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealm", Map.of("realm", name), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode realm = readTree(text(response));
                    assertEquals("length(12) and notUsername", realm.get("passwordPolicy").asText());
                    JsonNode bruteForce = realm.get("bruteForce");
                    assertTrue(bruteForce.get("enabled").asBoolean(), bruteForce.toString());
                    assertEquals("LINEAR", bruteForce.get("strategy").asText());
                    assertFalse(bruteForce.get("permanentLockout").asBoolean());
                    assertEquals(3, bruteForce.get("maxTemporaryLockouts").asInt());
                    assertEquals(5, bruteForce.get("failureFactor").asInt());
                    assertEquals(120, bruteForce.get("waitIncrementSeconds").asInt());
                    assertEquals(1800, bruteForce.get("maxFailureWaitSeconds").asInt());
                    assertEquals(3600, bruteForce.get("maxDeltaTimeSeconds").asInt());
                    assertEquals(30, bruteForce.get("minimumQuickLoginWaitSeconds").asInt());
                    assertEquals(500, bruteForce.get("quickLoginCheckMilliSeconds").asLong());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getServerVersion() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getServerVersion", Map.of(), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("{\"version\":\"26.8.2\"}", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealm", Map.of("realm", "nope"), response -> {
                    assertTrue(response.isError());
                    String message = text(response);
                    assertTrue(message.contains("404"), message);
                    assertTrue(message.contains("Realm not found"), message);
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRealmBlank() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRealm", Map.of("realm", " "), response -> assertTrue(response.isError()))
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createRealmRefusesWithoutConfirm() {
        String name = "tmp-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealm", Map.of("realm", name), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(realmExists(name));
    }

    @Test
    void createRealmWithConfirm() {
        String name = "tmp-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealm", Map.of("realm", name, "displayName", "Throwaway", "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode realm = readTree(text(response));
                    assertEquals(name, realm.get("name").asText());
                    assertEquals("Throwaway", realm.get("displayName").asText());
                    assertTrue(realm.get("enabled").asBoolean());
                    assertTrue(realm.at("/tokens").has("revokeRefreshToken"), realm.toString());
                    assertTrue(realm.has("unmanagedAttributePolicy"), realm.toString());
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(realmExists(name));
    }

    @Test
    void createRealmDuplicate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createRealm", Map.of("realm", "acme", "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void deleteRealmRefusesWithoutConfirm() {
        String name = createThrowawayRealm();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRealm", Map.of("realm", name, "confirm", false), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(realmExists(name));
    }

    @Test
    void deleteRealmWithConfirm() {
        String name = createThrowawayRealm();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRealm", Map.of("realm", name, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted realm " + name, text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(realmExists(name));
    }

    @Test
    void deleteRealmRefusesMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRealm", Map.of("realm", "master", "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(realmExists("master"));
    }

    static JsonNode readTree(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Not JSON: " + json, e);
        }
    }
}
