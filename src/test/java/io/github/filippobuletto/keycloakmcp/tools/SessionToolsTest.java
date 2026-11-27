package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SessionToolsTest {

    static final String PREFIX = "tmp-sess-";
    static final String APP = PREFIX + "app";
    static final String OTHER = PREFIX + "other";
    static final String SECRET = "tmp-sess-secret";
    static final String ALICE = PREFIX + "alice";
    static final String BOB = PREFIX + "bob";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @Inject
    Keycloak keycloak;

    String aliceId;
    String bobId;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Two confidential clients with direct access grants and two users who can sign in with password grants. */
    @BeforeEach
    void createFixture() {
        removeFixture();
        for (String clientId : List.of(APP, OTHER)) {
            createClient(acme(), clientId);
        }
        aliceId = createUser(acme(), ALICE);
        bobId = createUser(acme(), BOB);
    }

    /** A confidential client with {@link #SECRET} that allows password grants. */
    static void createClient(RealmResource realm, String clientId) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setPublicClient(false);
        client.setSecret(SECRET);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(false);
        realm.clients().create(client).close();
    }

    /** A user whose password is the username; returns the user id. */
    static String createUser(RealmResource realm, String username) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username + "@example.org");
        user.setEmailVerified(true);
        user.setFirstName("Tmp");
        user.setLastName("Session");
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(username);
        password.setTemporary(false);
        user.setCredentials(List.of(password));
        try (var response = realm.users().create(user)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    /** Deleting the users and clients also ends their sessions. */
    @AfterEach
    void removeFixture() {
        for (String username : List.of(ALICE, BOB)) {
            acme().users().searchByUsername(username, true).forEach(u -> acme().users().delete(u.getId()).close());
        }
        for (String clientId : List.of(APP, OTHER)) {
            acme().clients().findByClientId(clientId).forEach(c -> acme().clients().get(c.getId()).remove());
        }
    }

    /**
     * Signs in to acme with a password grant through a {@link #createClient} client and returns the token response;
     * {@code offline} asks for an offline token.
     */
    static JsonNode signIn(String clientId, String username, boolean offline) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", clientId);
        form.put("client_secret", SECRET);
        form.put("username", username);
        form.put("password", username);
        form.put("scope", offline ? "openid offline_access" : "openid");
        JsonNode tokens = tokenRequest(form);
        assertTrue(tokens.has("refresh_token"), "sign-in failed: " + tokens);
        return tokens;
    }

    /** Uses a refresh token and returns Keycloak's answer: tokens, or {@code error} and {@code error_description}. */
    static JsonNode refresh(String clientId, JsonNode tokens) {
        return tokenRequest(Map.of("grant_type", "refresh_token", "client_id", clientId, "client_secret", SECRET,
                "refresh_token", tokens.get("refresh_token").asText()));
    }

    static JsonNode tokenRequest(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String keycloakUrl = ConfigProvider.getConfig().getValue("keycloak.url", String.class);
        try {
            HttpResponse<String> response = HTTP.send(HttpRequest
                    .newBuilder(URI.create(keycloakUrl + "/realms/acme/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            return readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The session id a token response belongs to. */
    static String sessionId(JsonNode tokens) {
        return tokens.get("session_state").asText();
    }

    static List<String> field(JsonNode array, String name) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> node.get(name).asText())
                .toList();
    }

    static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    /** Sorted ids of the sessions of one type. */
    static List<String> idsOfType(JsonNode sessions, String type) {
        List<String> ids = new ArrayList<>();
        sessions.forEach(s -> {
            if (type.equals(s.get("type").asText())) {
                ids.add(s.get("id").asText());
            }
        });
        return ids.stream().sorted().toList();
    }

    @Test
    void listUserSessionsShowsRegularAndOfflineSessionsByClientId() {
        JsonNode first = signIn(APP, ALICE, false);
        JsonNode second = signIn(APP, ALICE, false);
        JsonNode offline = signIn(APP, ALICE, true);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listUserSessions", Map.of("realm", "acme", "userId", aliceId), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode sessions = readTree(text(response));
                    assertEquals(3, sessions.size(), text(response));
                    assertEquals(List.of(sessionId(first), sessionId(second)).stream().sorted().toList(),
                            idsOfType(sessions, "REGULAR"), text(response));
                    assertEquals(List.of(sessionId(offline)), idsOfType(sessions, "OFFLINE"), text(response));
                    for (JsonNode session : sessions) {
                        assertEquals(List.of(APP), texts(session.get("clientIds")), text(response));
                        assertEquals(ALICE, session.get("username").asText());
                        assertEquals(aliceId, session.get("userId").asText());
                        assertTrue(session.get("start").asLong() > 0, text(response));
                    }
                    assertFalse(text(response).contains(acme().clients().findByClientId(APP).get(0).getId()),
                            "client UUIDs must not appear: " + text(response));
                })
                .toolsCall("listUserSessions", Map.of("realm", "acme", "userId", bobId), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(readTree(text(response)).isEmpty(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listClientSessionsFiltersByTypeAndPages() {
        signIn(APP, ALICE, false);
        signIn(APP, BOB, false);
        JsonNode offline = signIn(APP, ALICE, true);
        signIn(OTHER, BOB, false);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClientSessions", Map.of("realm", "acme", "clientId", APP), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode sessions = readTree(text(response));
                    assertEquals(2, sessions.size(), text(response));
                    assertEquals(List.of(ALICE, BOB), field(sessions, "username").stream().sorted().toList());
                    sessions.forEach(s -> assertEquals("REGULAR", s.get("type").asText()));
                })
                .toolsCall("listClientSessions", Map.of("realm", "acme", "clientId", APP, "type", "OFFLINE"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode sessions = readTree(text(response));
                            assertEquals(List.of(sessionId(offline)), field(sessions, "id"), text(response));
                            assertEquals("OFFLINE", sessions.get(0).get("type").asText());
                        })
                .toolsCall("listClientSessions", Map.of("realm", "acme", "clientId", APP, "first", 1, "max", 1),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals(1, readTree(text(response)).size(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getSessionStatsCountsActiveAndOfflineSessionsPerClient() {
        signIn(APP, ALICE, false);
        signIn(APP, BOB, false);
        signIn(APP, ALICE, true);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getSessionStats", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode stats = readTree(text(response));
                    JsonNode app = IdentityProviderToolsTest.withField(stats, "clientId", APP);
                    assertEquals(2, app.get("active").asLong(), text(response));
                    assertEquals(1, app.get("offline").asLong(), text(response));
                    assertFalse(field(stats, "clientId").contains(OTHER), text(response));
                })
                .toolsCall("getSessionStats", Map.of("realm", "master"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(readTree(text(response)).isArray(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    /** Ids of every session {@code listUserSessions} shows for the user. */
    List<String> listedIds(String userId) {
        List<String> ids = new ArrayList<>();
        McpStreamableTestClient client = McpClients.connected();
        client.when()
                .toolsCall("listUserSessions", Map.of("realm", "acme", "userId", userId), response -> {
                    assertFalse(response.isError(), text(response));
                    ids.addAll(field(readTree(text(response)), "id"));
                })
                .thenAssertResults();
        client.disconnect();
        return ids;
    }

    @Test
    void deleteSessionEndsARegularSession() {
        JsonNode ended = signIn(APP, ALICE, false);
        JsonNode kept = signIn(APP, ALICE, false);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteSession", Map.of("realm", "acme", "sessionId", sessionId(ended)), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(text(response).contains(sessionId(ended)), text(response));
                })
                .thenAssertResults();
        client.disconnect();

        assertTrue(refresh(APP, ended).has("error"), "the ended session still refreshes");
        assertFalse(refresh(APP, kept).has("error"), "the other session stopped refreshing");
        assertEquals(List.of(sessionId(kept)), listedIds(aliceId));
    }

    @Test
    void deleteSessionWithTheWrongTypeSaysWhichToUseAndEndsNothing() {
        JsonNode regular = signIn(APP, ALICE, false);
        JsonNode offline = signIn(APP, ALICE, true);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteSession", Map.of("realm", "acme", "sessionId", sessionId(offline)), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("offline=true"), text(response));
                })
                .toolsCall("deleteSession", Map.of("realm", "acme", "sessionId", sessionId(regular), "offline", true),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("offline=false"), text(response));
                        })
                .thenAssertResults();
        client.disconnect();

        assertEquals(List.of(sessionId(regular), sessionId(offline)).stream().sorted().toList(),
                listedIds(aliceId).stream().sorted().toList());
    }

    @Test
    void deleteSessionEndsAnOfflineSession() {
        JsonNode offline = signIn(APP, ALICE, true);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteSession", Map.of("realm", "acme", "sessionId", sessionId(offline), "offline", true),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        client.disconnect();

        assertTrue(refresh(APP, offline).has("error"), "the ended offline session still refreshes");
        assertTrue(listedIds(aliceId).isEmpty());
    }

    @Test
    void deleteSessionFailsForAnUnknownIdAndInMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteSession", Map.of("realm", "acme", "sessionId", "no-such-session"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("No session has id no-such-session in realm acme"),
                            text(response));
                })
                .toolsCall("deleteSession", Map.of("realm", "no-such-realm", "sessionId", "no-such-session"),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("404"), text(response));
                        })
                .toolsCall("deleteSession", Map.of("realm", "master", "sessionId", "no-such-session"),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void logoutUserEndsTheUsersRegularAndOfflineSessionsOnly() {
        JsonNode regular = signIn(APP, ALICE, false);
        signIn(APP, ALICE, false);
        JsonNode offline = signIn(APP, ALICE, true);
        JsonNode bobs = signIn(APP, BOB, false);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("logoutUser", Map.of("realm", "acme", "userId", aliceId), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();
        assertEquals(3, listedIds(aliceId).size());

        client.when()
                .toolsCall("logoutUser", Map.of("realm", "acme", "userId", aliceId, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(text(response).contains("2 regular and 1 offline session."), text(response));
                })
                .thenAssertResults();
        client.disconnect();

        assertTrue(listedIds(aliceId).isEmpty());
        assertTrue(refresh(APP, regular).has("error"), "a regular session still refreshes");
        assertTrue(refresh(APP, offline).has("error"), "the offline session still refreshes");
        assertFalse(refresh(APP, bobs).has("error"), "another user's session stopped refreshing");
    }

    /** Ends every acme session, so the test restores acme's {@code notBefore}, which logout-all moves to now. */
    @Test
    void logoutRealmEndsEverySessionInTheRealm() {
        Integer notBefore = acme().toRepresentation().getNotBefore();
        try {
            JsonNode regular = signIn(APP, ALICE, false);
            JsonNode offline = signIn(APP, ALICE, true);
            signIn(OTHER, BOB, false);
            McpStreamableTestClient client = McpClients.connected();

            client.when()
                    .toolsCall("logoutRealm", Map.of("realm", "acme"), response -> {
                        assertTrue(response.isError(), text(response));
                        assertTrue(text(response).contains("confirm=true"), text(response));
                    })
                    .thenAssertResults();
            assertFalse(acme().getClientSessionStats().isEmpty());

            client.when()
                    .toolsCall("logoutRealm", Map.of("realm", "acme", "confirm", true), response -> {
                        assertFalse(response.isError(), text(response));
                        assertTrue(text(response).contains("acme"), text(response));
                    })
                    .thenAssertResults();
            client.when()
                    .toolsCall("getSessionStats", Map.of("realm", "acme"), response -> {
                        assertFalse(response.isError(), text(response));
                        assertTrue(readTree(text(response)).isEmpty(), text(response));
                    })
                    .thenAssertResults();
            client.disconnect();

            assertTrue(refresh(APP, regular).has("error"), "a regular session still refreshes");
            assertTrue(refresh(APP, offline).has("error"), "the offline session still refreshes");
        } finally {
            RealmRepresentation restore = new RealmRepresentation();
            restore.setNotBefore(notBefore);
            acme().update(restore);
        }
    }

    @Test
    void logoutFailsForAnUnknownUserOrRealmAndInMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("logoutUser", Map.of("realm", "acme", "userId", "00000000-0000-0000-0000-000000000000",
                        "confirm", true), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains(
                                    "User 00000000-0000-0000-0000-000000000000 not found in realm acme"),
                                    text(response));
                        })
                .toolsCall("logoutRealm", Map.of("realm", "no-such-realm", "confirm", true), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .toolsCall("logoutUser", Map.of("realm", "master", "userId", aliceId, "confirm", true),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("logoutRealm", Map.of("realm", "master", "confirm", true), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("refused for the master realm"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void anUnknownUserClientOrRealmFails() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listUserSessions", Map.of("realm", "acme", "userId", "00000000-0000-0000-0000-000000000000"),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains(
                                    "User 00000000-0000-0000-0000-000000000000 not found in realm acme"),
                                    text(response));
                        })
                .toolsCall("listClientSessions", Map.of("realm", "acme", "clientId", "no-such-client"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("Client no-such-client not found in realm acme"),
                            text(response));
                })
                .toolsCall("getSessionStats", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .toolsCall("listUserSessions", Map.of("realm", "no-such-realm", "userId", aliceId), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }
}
