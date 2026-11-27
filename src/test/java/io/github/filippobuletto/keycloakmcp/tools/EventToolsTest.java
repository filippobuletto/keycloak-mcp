package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EventToolsTest {

    @Inject
    Keycloak keycloak;

    RealmEventsConfigRepresentation saved;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    @BeforeEach
    void saveSettings() {
        saved = acme().getRealmEventsConfig();
    }

    @AfterEach
    void restoreSettings() {
        try {
            acme().users().searchByUsername(ALICE, true).forEach(u -> acme().users().delete(u.getId()).close());
            acme().clients().findByClientId(APP).forEach(c -> acme().clients().get(c.getId()).remove());
            acme().groups().groups(GROUP, 0, 10).stream().filter(g -> g.getName().equals(GROUP))
                    .forEach(g -> acme().groups().group(g.getId()).remove());
        } finally {
            restore(acme(), saved);
        }
    }

    static final String APP = "tmp-ev-app";
    static final String ALICE = "tmp-ev-alice";
    static final String GROUP = "tmp-ev-group";

    /** Turns admin events with payloads on, then creates and updates {@link #GROUP}; returns its resource path. */
    String groupChangesRecorded() {
        RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
        on.setAdminEventsEnabled(true);
        on.setAdminEventsDetailsEnabled(true);
        on.setEnabledEventTypes(null);
        acme().updateRealmEventsConfig(on);
        GroupRepresentation group = new GroupRepresentation();
        group.setName(GROUP);
        String id;
        try (var response = acme().groups().add(group)) {
            id = CreatedResponseUtil.getCreatedId(response);
        }
        GroupRepresentation stored = acme().groups().group(id).toRepresentation();
        stored.setAttributes(Map.of("k", List.of("v")));
        acme().groups().group(id).update(stored);
        return "groups/" + id;
    }

    /** Turns login events on in acme, without fixing the type list. */
    void recordLoginEvents() {
        RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
        on.setEventsEnabled(true);
        on.setEnabledEventTypes(null);
        acme().updateRealmEventsConfig(on);
    }

    /** Records two sign-ins and then one wrong password of {@link #ALICE} through {@link #APP}; returns her id. */
    String signInsRecorded() {
        recordLoginEvents();
        SessionToolsTest.createClient(acme(), APP);
        String aliceId = SessionToolsTest.createUser(acme(), ALICE);
        SessionToolsTest.signIn(APP, ALICE, false);
        SessionToolsTest.signIn(APP, ALICE, false);
        JsonNode failed = SessionToolsTest.tokenRequest(Map.of("grant_type", "password", "client_id", APP,
                "client_secret", SessionToolsTest.SECRET, "username", ALICE, "password", "wrong"));
        assertEquals("invalid_grant", failed.path("error").asText(), failed.toString());
        return aliceId;
    }

    /**
     * Puts back event settings read with GET and deletes the stored events. GET fills an empty type list with
     * Keycloak's defaults, and acme stores no list, so the restore sends an empty one.
     */
    static void restore(RealmResource realm, RealmEventsConfigRepresentation saved) {
        RealmEventsConfigRepresentation body = new RealmEventsConfigRepresentation();
        body.setEventsEnabled(saved.isEventsEnabled());
        body.setEventsExpiration(saved.getEventsExpiration());
        body.setEventsListeners(saved.getEventsListeners());
        body.setEnabledEventTypes(List.of());
        body.setAdminEventsEnabled(Boolean.TRUE.equals(saved.isAdminEventsEnabled()));
        body.setAdminEventsDetailsEnabled(Boolean.TRUE.equals(saved.isAdminEventsDetailsEnabled()));
        realm.updateRealmEventsConfig(body);
        realm.clearEvents();
        realm.clearAdminEvents();
    }

    static JsonNode result(McpStreamableTestClient client, String tool, Map<String, Object> args) {
        JsonNode[] result = new JsonNode[1];
        client.when()
                .toolsCall(tool, args, response -> {
                    assertFalse(response.isError(), text(response));
                    result[0] = readTree(text(response));
                })
                .thenAssertResults();
        return result[0];
    }

    static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    @Test
    void getEventSettingsShowsAcmeRecordingNothing() {
        McpStreamableTestClient client = McpClients.connected();

        JsonNode settings = result(client, "getEventSettings", Map.of("realm", "acme"));

        assertFalse(settings.get("eventsEnabled").asBoolean(), settings.toString());
        assertFalse(settings.path("adminEventsEnabled").asBoolean(), settings.toString());
        assertTrue(settings.get("eventsListeners").toString().contains("jboss-logging"), settings.toString());
        assertTrue(settings.get("enabledEventTypes").toString().contains("LOGIN_ERROR"), settings.toString());
        client.disconnect();
    }

    @Test
    void updateEventSettingsChangesOnlyTheGivenSettings() {
        McpStreamableTestClient client = McpClients.connected();

        JsonNode on = result(client, "updateEventSettings",
                Map.of("realm", "acme", "eventsEnabled", true, "eventsExpiration", 3600));
        assertTrue(on.get("eventsEnabled").asBoolean(), on.toString());
        assertEquals(3600, on.get("eventsExpiration").asLong(), on.toString());
        assertEquals(saved.getEventsListeners(), strings(on.get("eventsListeners")), on.toString());
        assertFalse(on.path("adminEventsEnabled").asBoolean(), on.toString());

        JsonNode admin = result(client, "updateEventSettings", Map.of("realm", "acme", "adminEventsEnabled", true));
        assertTrue(admin.get("adminEventsEnabled").asBoolean(), admin.toString());
        assertTrue(admin.get("eventsEnabled").asBoolean(), "eventsEnabled was reset: " + admin);
        assertEquals(3600, admin.get("eventsExpiration").asLong(), "eventsExpiration was reset: " + admin);
        client.disconnect();
    }

    @Test
    void anUnknownEventTypeOrListenerFailsNamingIt() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateEventSettings",
                        Map.of("realm", "acme", "eventsEnabled", true, "enabledEventTypes", List.of("LOGIN", "NOT_A_TYPE")),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("NOT_A_TYPE"), text(response));
                        })
                .toolsCall("updateEventSettings",
                        Map.of("realm", "acme", "eventsEnabled", true, "eventsListeners", List.of("no-such-listener")),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("no-such-listener"), text(response));
                        })
                .thenAssertResults();
        client.disconnect();

        assertFalse(acme().getRealmEventsConfig().isEventsEnabled(), "a refused update changed the settings");
    }

    @Test
    void stoppingRecordingNeedsConfirmation() {
        RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
        on.setEventsEnabled(true);
        on.setEnabledEventTypes(null);
        acme().updateRealmEventsConfig(on);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateEventSettings", Map.of("realm", "acme", "eventsEnabled", false), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("login events off"), text(response));
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();
        assertTrue(acme().getRealmEventsConfig().isEventsEnabled(), "an unconfirmed update turned events off");

        JsonNode off = result(client, "updateEventSettings",
                Map.of("realm", "acme", "eventsEnabled", false, "confirm", true));
        assertFalse(off.get("eventsEnabled").asBoolean(), off.toString());
        client.disconnect();
    }

    @Test
    void writesAreRefusedInMasterAndAnUnknownRealmFails() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getEventSettings", Map.of("realm", "master"),
                        response -> assertFalse(response.isError(), text(response)))
                .toolsCall("updateEventSettings", Map.of("realm", "master", "eventsEnabled", true), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("refused for the master realm"), text(response));
                })
                .toolsCall("getEventSettings", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .toolsCall("updateEventSettings", Map.of("realm", "no-such-realm", "eventsEnabled", true), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    void restorePutsBackWhatGetShowedBefore() {
        RealmEventsConfigRepresentation changed = acme().getRealmEventsConfig();
        changed.setEventsEnabled(true);
        changed.setEventsExpiration(60L);
        changed.setEnabledEventTypes(List.of("LOGIN"));
        changed.setAdminEventsEnabled(true);
        acme().updateRealmEventsConfig(changed);

        restore(acme(), saved);

        RealmEventsConfigRepresentation after = acme().getRealmEventsConfig();
        assertEquals(saved.isEventsEnabled(), after.isEventsEnabled());
        assertEquals(saved.getEventsExpiration(), after.getEventsExpiration());
        assertEquals(saved.getEventsListeners(), after.getEventsListeners());
        assertEquals(saved.getEnabledEventTypes(), after.getEnabledEventTypes());
        assertEquals(saved.isAdminEventsEnabled(), after.isAdminEventsEnabled());
        assertEquals(saved.isAdminEventsDetailsEnabled(), after.isAdminEventsDetailsEnabled());
    }

    @Test
    void listEventsShowsRecordedSignInsNewestFirst() {
        String aliceId = signInsRecorded();
        McpStreamableTestClient client = McpClients.connected();

        JsonNode all = result(client, "listEvents", Map.of("realm", "acme", "clientId", APP));
        assertEquals(List.of("LOGIN_ERROR", "LOGIN", "LOGIN"), types(all), all.toString());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).get("time").asLong() >= all.get(i).get("time").asLong(), all.toString());
        }
        JsonNode login = all.get(1);
        assertEquals(APP, login.get("clientId").asText(), login.toString());
        assertEquals(aliceId, login.get("userId").asText(), login.toString());
        assertFalse(login.get("sessionId").asText().isEmpty(), login.toString());
        assertFalse(login.get("ipAddress").asText().isEmpty(), login.toString());
        assertEquals(ALICE, login.get("details").get("username").asText(), login.toString());
        assertFalse(login.has("realmId"), "realmId is redundant: " + login);

        JsonNode errors = result(client, "listEvents",
                Map.of("realm", "acme", "clientId", APP, "types", List.of("LOGIN_ERROR")));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("invalid_user_credentials", errors.get(0).get("error").asText(), errors.toString());

        assertEquals(3, result(client, "listEvents", Map.of("realm", "acme", "userId", aliceId)).size());
        assertEquals(2, result(client, "listEvents",
                Map.of("realm", "acme", "userId", aliceId, "types", List.of("LOGIN"))).size());
        JsonNode page = result(client, "listEvents", Map.of("realm", "acme", "clientId", APP, "first", 1, "max", 1));
        assertEquals(List.of("LOGIN"), types(page), page.toString());
        client.disconnect();
    }

    @Test
    void listEventsRejectsUnknownTypesAndBadDates() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listEvents", Map.of("realm", "acme", "types", List.of("LOGIN", "NOT_A_TYPE")), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("NOT_A_TYPE"), text(response));
                })
                .toolsCall("listEvents", Map.of("realm", "acme", "dateFrom", "yesterday"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("Invalid value for 'dateFrom'"), text(response));
                })
                .toolsCall("listEvents", Map.of("realm", "master"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(readTree(text(response)).isArray(), text(response));
                })
                .toolsCall("listEvents", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    void listAdminEventsShowsWhoChangedWhat() {
        String path = groupChangesRecorded();
        McpStreamableTestClient client = McpClients.connected();

        JsonNode both = result(client, "listAdminEvents", Map.of("realm", "acme", "resourcePath", path));
        assertEquals(List.of("UPDATE", "CREATE"), field(both, "operationType"), both.toString());
        JsonNode created = both.get(1);
        assertEquals("GROUP", created.get("resourceType").asText(), created.toString());
        assertEquals(path, created.get("resourcePath").asText(), created.toString());
        assertEquals("master", created.get("authRealm").asText(), created.toString());
        assertEquals("admin-cli", created.get("authClientId").asText(), created.toString());
        assertFalse(created.get("authUserId").asText().isEmpty(), created.toString());
        assertTrue(created.get("representation").isNull(), created.toString());

        JsonNode creates = result(client, "listAdminEvents",
                Map.of("realm", "acme", "resourcePath", path, "operationTypes", List.of("CREATE")));
        assertEquals(List.of("CREATE"), field(creates, "operationType"), creates.toString());
        JsonNode anyGroup = result(client, "listAdminEvents", Map.of("realm", "acme", "resourcePath", "groups/*",
                "resourceTypes", List.of("GROUP"), "max", 500));
        assertEquals(2, field(anyGroup, "resourcePath").stream().filter(path::equals).count(), anyGroup.toString());
        JsonNode byAdmin = result(client, "listAdminEvents",
                Map.of("realm", "acme", "resourcePath", path, "authRealm", "master", "authClientId", "admin-cli"));
        assertEquals(2, byAdmin.size(), byAdmin.toString());
        JsonNode byUser = result(client, "listAdminEvents",
                Map.of("realm", "acme", "resourcePath", path, "authUserId", created.get("authUserId").asText()));
        assertEquals(2, byUser.size(), byUser.toString());
        JsonNode withJson = result(client, "listAdminEvents",
                Map.of("realm", "acme", "resourcePath", path, "includeRepresentation", true));
        assertTrue(withJson.get(1).get("representation").asText().contains(GROUP), withJson.toString());
        client.disconnect();
    }

    @Test
    void listAdminEventsRejectsUnknownNames() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listAdminEvents", Map.of("realm", "acme", "authRealm", "master", "authClientId", "no-such-cli"),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("Client no-such-cli not found in realm master"),
                                    text(response));
                        })
                .toolsCall("listAdminEvents", Map.of("realm", "acme", "authRealm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("no-such-realm"), text(response));
                })
                .toolsCall("listAdminEvents", Map.of("realm", "acme", "authClientId", "admin-cli"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("authRealm"), text(response));
                })
                .toolsCall("listAdminEvents", Map.of("realm", "acme", "resourceTypes", List.of("NOPE")), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("NOPE"), text(response));
                })
                .toolsCall("listAdminEvents", Map.of("realm", "acme", "operationTypes", List.of("REMOVE")), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("REMOVE"), text(response));
                })
                .toolsCall("listAdminEvents", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    void clearEventsDeletesOneKindAfterConfirmation() {
        signInsRecorded();
        String path = groupChangesRecorded();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("clearEvents", Map.of("realm", "acme", "kind", "LOGIN"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();
        assertEquals(3, result(client, "listEvents", Map.of("realm", "acme", "clientId", APP)).size());

        client.when()
                .toolsCall("clearEvents", Map.of("realm", "acme", "kind", "LOGIN", "confirm", true),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        assertEquals(0, result(client, "listEvents", Map.of("realm", "acme", "clientId", APP)).size());
        assertEquals(2, result(client, "listAdminEvents", Map.of("realm", "acme", "resourcePath", path)).size());

        client.when()
                .toolsCall("clearEvents", Map.of("realm", "acme", "kind", "ADMIN", "confirm", true),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        assertEquals(0, result(client, "listAdminEvents", Map.of("realm", "acme", "resourcePath", path)).size());
        client.disconnect();
    }

    @Test
    void clearEventsIsRefusedInMasterAndFailsForAnUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("clearEvents", Map.of("realm", "master", "kind", "LOGIN", "confirm", true), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("refused for the master realm"), text(response));
                })
                .toolsCall("clearEvents", Map.of("realm", "no-such-realm", "kind", "ADMIN", "confirm", true),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("404"), text(response));
                        })
                .thenAssertResults();
        client.disconnect();
    }

    static List<String> field(JsonNode events, String name) {
        return StreamSupport.stream(events.spliterator(), false).map(e -> e.get(name).asText()).toList();
    }

    static List<String> types(JsonNode events) {
        return StreamSupport.stream(events.spliterator(), false).map(e -> e.get("type").asText()).toList();
    }
}
