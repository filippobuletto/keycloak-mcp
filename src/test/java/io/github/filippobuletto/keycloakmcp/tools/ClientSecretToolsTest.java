package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.representations.idm.ClientRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** With {@code MCP_CLIENT_SECRETS_ENABLED=true}: the secret tools, their guards and their results. */
@QuarkusTest
@TestProfile(ClientSecretsProfile.class)
class ClientSecretToolsTest {

    @Inject
    Keycloak keycloak;

    ClientResource client(String realm, String clientId) {
        String id = keycloak.realm(realm).clients().findByClientId(clientId).getFirst().getId();
        return keycloak.realm(realm).clients().get(id);
    }

    /** A confidential acme client with service accounts, so a client credentials grant proves which secret works. */
    String createThrowawayClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("tmp-" + UUID.randomUUID());
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        keycloak.realm("acme").clients().create(client).close();
        return client.getClientId();
    }

    @AfterEach
    void deleteThrowawayClients() {
        keycloak.realm("acme").clients().findAll().stream()
                .filter(client -> client.getClientId().startsWith("tmp-"))
                .forEach(client -> keycloak.realm("acme").clients().get(client.getId()).remove());
    }

    /** Status of a client credentials grant in the acme realm: 200 when the secret is current, 401 otherwise. */
    static int clientCredentialsGrant(String clientId, String secret) throws Exception {
        String tokenUrl = ConfigProvider.getConfig().getValue("keycloak.url", String.class)
                + "/realms/acme/protocol/openid-connect/token";
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    /**
     * Collects every server log record from DEBUG up while it is open; the root logger level is raised to DEBUG
     * meanwhile, so a library that logs bodies at DEBUG is caught too.
     */
    static final class LogCapture extends Handler implements AutoCloseable {

        private final Logger root = Logger.getLogger("");
        private final Level previous = root.getLevel();
        private final List<String> lines = new CopyOnWriteArrayList<>();

        LogCapture() {
            setLevel(Level.ALL);
            root.setLevel(Level.FINE);
            root.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            // Raw message plus parameters: a secret passed as a format argument is caught whatever the style
            String message = record.getMessage() + " " + Arrays.toString(record.getParameters());
            lines.add(record.getLoggerName() + " " + message
                    + (record.getThrown() == null ? "" : " " + record.getThrown()));
        }

        String text() {
            return String.join("\n", lines);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            root.removeHandler(this);
            root.setLevel(previous);
        }
    }

    @Test
    void secretToolIsListedNextToV1Tools() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsList(page -> {
                    assertTrue(page.tools().stream().anyMatch(tool -> tool.name().equals("getClient")));
                    assertTrue(page.tools().stream().anyMatch(tool -> tool.name().equals("getClientSecret")),
                            page.tools().stream().map(tool -> tool.name()).toList().toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientSecretReturnsTheSecretKeycloakHolds() {
        String expected = client("acme", "reports-service").getSecret().getValue();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientSecret",
                        Map.of("realm", "acme", "clientId", "reports-service", "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode secret = readTree(text(response));
                            assertEquals("acme", secret.get("realm").asText());
                            assertEquals("reports-service", secret.get("clientId").asText());
                            assertEquals(expected, secret.get("secret").asText());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientSecretNeedsConfirm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientSecret",
                        Map.of("realm", "acme", "clientId", "reports-service", "confirm", false), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("confirm=true"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientSecretRefusesMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientSecret",
                        Map.of("realm", "master", "clientId", "admin-cli", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientSecretRefusesAPublicClient() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientSecret",
                        Map.of("realm", "acme", "clientId", "acme-web", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("acme-web is a public client"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void regenerateClientSecretReturnsTheNewWorkingSecretAndLogsNoSecret() throws Exception {
        String clientId = createThrowawayClient();
        String old = client("acme", clientId).getSecret().getValue();
        AtomicReference<String> read = new AtomicReference<>();
        AtomicReference<String> fresh = new AtomicReference<>();
        McpStreamableTestClient client = McpClients.connected();

        String log;
        try (LogCapture capture = new LogCapture()) {
            client.when()
                    .toolsCall("getClientSecret", Map.of("realm", "acme", "clientId", clientId, "confirm", true),
                            response -> {
                                assertFalse(response.isError(), text(response));
                                read.set(readTree(text(response)).get("secret").asText());
                            })
                    .thenAssertResults();
            client.when()
                    .toolsCall("regenerateClientSecret",
                            Map.of("realm", "acme", "clientId", clientId, "confirm", true), response -> {
                                assertFalse(response.isError(), text(response));
                                JsonNode secret = readTree(text(response));
                                assertEquals("acme", secret.get("realm").asText());
                                assertEquals(clientId, secret.get("clientId").asText());
                                fresh.set(secret.get("secret").asText());
                            })
                    .thenAssertResults();
            log = capture.text();
        }
        client.disconnect();

        assertEquals(old, read.get());
        assertNotEquals(old, fresh.get());
        assertEquals(fresh.get(), client("acme", clientId).getSecret().getValue());
        assertEquals(200, clientCredentialsGrant(clientId, fresh.get()));
        assertEquals(401, clientCredentialsGrant(clientId, old));
        assertFalse(log.isEmpty(), "the capture saw no log records, so it proves nothing");
        assertFalse(log.contains(old), "the old secret reached the log");
        assertFalse(log.contains(fresh.get()), "the new secret reached the log");
    }

    @Test
    void regenerateClientSecretRefusesWithoutChangingTheSecret() {
        String clientId = createThrowawayClient();
        String old = client("acme", clientId).getSecret().getValue();
        String publicSecretBefore = client("acme", "acme-web").toRepresentation().getSecret();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("regenerateClientSecret",
                        Map.of("realm", "acme", "clientId", clientId, "confirm", false), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("confirm=true"), text(response));
                        })
                .toolsCall("regenerateClientSecret",
                        Map.of("realm", "master", "clientId", "admin-cli", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("regenerateClientSecret",
                        Map.of("realm", "acme", "clientId", "acme-web", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("acme-web is a public client"), text(response));
                        })
                .thenAssertResults();
        client.disconnect();

        assertEquals(old, client("acme", clientId).getSecret().getValue());
        assertEquals(publicSecretBefore, client("acme", "acme-web").toRepresentation().getSecret());
    }
}
