package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClientToolsTest {

    @Inject
    Keycloak keycloak;

    ClientRepresentation find(String clientId) {
        return keycloak.realm("acme").clients().findByClientId(clientId).stream().findFirst().orElse(null);
    }

    String createThrowawayClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("tmp-" + UUID.randomUUID());
        client.setName("Throwaway");
        client.setRedirectUris(List.of("https://old.example/*"));
        keycloak.realm("acme").clients().create(client).close();
        return client.getClientId();
    }

    /** The client's real secret must not appear in the JSON, and no key may name a secret. */
    void assertNoSecret(String clientId, String json) {
        String secret = keycloak.realm("acme").clients().get(find(clientId).getId()).getSecret().getValue();
        assertFalse(secret == null || secret.isBlank(), "no secret to look for on " + clientId);
        assertFalse(json.contains(secret), json);
        assertFalse(JSON_KEY.matcher(json).results()
                .anyMatch(key -> key.group(1).toLowerCase(Locale.ROOT).contains("secret")), json);
    }

    static final Pattern JSON_KEY = Pattern.compile("\"([^\"]*+)\"\\s*+:");

    @AfterEach
    void deleteThrowawayClients() {
        keycloak.realm("acme").clients().findAll().stream()
                .filter(client -> client.getClientId().startsWith("tmp-"))
                .forEach(client -> keycloak.realm("acme").clients().get(client.getId()).remove());
    }

    @Test
    void listClients() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClients", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    String json = text(response);
                    assertTrue(json.contains("\"clientId\":\"acme-web\""), json);
                    assertTrue(json.contains("\"clientId\":\"reports-service\""), json);
                    assertFalse(json.toLowerCase(Locale.ROOT).contains("secret"), json);
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listClientsUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClients", Map.of("realm", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getConfidentialClientWithoutSecret() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", "reports-service"), response -> {
                    assertFalse(response.isError(), text(response));
                    String json = text(response);
                    JsonNode detail = readTree(json);
                    assertEquals("reports-service", detail.get("clientId").asText());
                    assertFalse(detail.get("publicClient").asBoolean());
                    assertTrue(detail.get("serviceAccountsEnabled").asBoolean());
                    assertNoSecret("reports-service", json);
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getPublicClientDetail() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", "acme-web"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode detail = readTree(text(response));
                    assertTrue(detail.get("publicClient").asBoolean());
                    assertEquals("http://localhost:3000", detail.get("rootUrl").asText());
                    assertEquals("http://localhost:3000/*", detail.at("/redirectUris/0").asText());
                    assertEquals("http://localhost:3000", detail.at("/webOrigins/0").asText());
                    assertFalse(detail.get("implicitFlowEnabled").asBoolean(), detail.toString());
                    assertTrue(detail.get("fullScopeAllowed").asBoolean(), detail.toString());
                    assertEquals("client-secret", detail.get("clientAuthenticatorType").asText());
                    assertEquals(0, detail.get("protocolMappers").size(), detail.toString());
                    assertEquals(0, detail.at("/scopeMappings/realmRoles").size(), detail.toString());
                    assertEquals(0, detail.at("/scopeMappings/clientRoles").size(), detail.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientShowsOwnMappersAndScopeMappings() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", "security-admin-console"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode mapper = readTree(text(response)).at("/protocolMappers/0");
                    assertEquals("locale", mapper.get("name").asText(), mapper.toString());
                    assertEquals("oidc-usermodel-attribute-mapper", mapper.get("type").asText());
                    assertEquals("locale", mapper.at("/config/user.attribute").asText());
                })
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", "account-console"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode scope = readTree(text(response)).get("scopeMappings");
                    assertEquals("[\"manage-account\",\"view-groups\"]", scope.at("/clientRoles/account").toString(),
                            scope.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getSamlClientShowsSignatureAndEncryptionSettings() {
        ClientRepresentation saml = new ClientRepresentation();
        saml.setClientId("tmp-saml-" + UUID.randomUUID());
        saml.setProtocol("saml");
        saml.setAttributes(Map.of(
                "saml.assertion.signature", "false",
                // Keycloak generates the signing certificate only for clients that must sign requests
                "saml.client.signature", "true",
                "saml.encrypt", "true",
                "saml.onetimeuse.condition", "true",
                "saml.signature.algorithm", "RSA_SHA1"));
        keycloak.realm("acme").clients().create(saml).close();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", saml.getClientId()), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode attributes = readTree(text(response)).get("attributes");
                    assertEquals("false", attributes.get("saml.assertion.signature").asText(), attributes.toString());
                    assertEquals("true", attributes.get("saml.client.signature").asText());
                    assertEquals("true", attributes.get("saml.encrypt").asText());
                    assertEquals("true", attributes.get("saml.onetimeuse.condition").asText());
                    assertEquals("RSA_SHA1", attributes.get("saml.signature.algorithm").asText());
                    assertTrue(find(saml.getClientId()).getAttributes().containsKey("saml.signing.certificate"));
                    assertFalse(attributes.has("saml.signing.certificate"), attributes.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClient", Map.of("realm", "acme", "clientId", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createConfidentialClientWithServiceAccounts() {
        String clientId = "tmp-billing-api-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClient", Map.of("realm", "acme", "clientId", clientId, "publicClient", false,
                        "serviceAccountsEnabled", true), response -> {
                            assertFalse(response.isError(), text(response));
                            String json = text(response);
                            JsonNode detail = readTree(json);
                            assertEquals(clientId, detail.get("clientId").asText());
                            assertFalse(detail.get("publicClient").asBoolean());
                            assertTrue(detail.get("serviceAccountsEnabled").asBoolean());
                            assertNoSecret(clientId, json);
                        })
                .thenAssertResults();

        client.disconnect();
        ClientRepresentation created = find(clientId);
        assertFalse(created.isPublicClient());
        assertTrue(created.isServiceAccountsEnabled());
        assertFalse(created.isStandardFlowEnabled());
        assertFalse(created.isDirectAccessGrantsEnabled());
    }

    @Test
    void createClientIsConfidentialUnlessPublicIsRequested() {
        String clientId = "tmp-kiosk-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClient", Map.of("realm", "acme", "clientId", clientId), response -> {
                    assertFalse(response.isError(), text(response));
                    assertFalse(readTree(text(response)).get("publicClient").asBoolean(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertFalse(find(clientId).isPublicClient());
    }

    @Test
    void createClientDuplicate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClient", Map.of("realm", "acme", "clientId", "acme-web"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void clientWritesRefuseMasterRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClient", Map.of("realm", "master", "clientId", "tmp-master"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .toolsCall("deleteClient", Map.of("realm", "master", "clientId", "admin-cli", "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("master"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(keycloak.realm("master").clients().findByClientId("tmp-master").isEmpty());
        assertFalse(keycloak.realm("master").clients().findByClientId("admin-cli").isEmpty());
    }

    @Test
    void updateClientChangesOnlySuppliedFields() {
        String clientId = createThrowawayClient();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateClient", Map.of("realm", "acme", "clientId", clientId, "webOrigins",
                        List.of("https://new.example")), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("https://new.example", readTree(text(response)).at("/webOrigins/0").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        ClientRepresentation updated = find(clientId);
        assertEquals("Throwaway", updated.getName());
        assertEquals(List.of("https://old.example/*"), updated.getRedirectUris());
        assertEquals(List.of("https://new.example"), updated.getWebOrigins());
    }

    @Test
    void deleteClientRefusesWithoutConfirm() {
        String clientId = createThrowawayClient();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteClient", Map.of("realm", "acme", "clientId", clientId), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(find(clientId) != null);
    }

    @Test
    void deleteClientWithConfirm() {
        String clientId = createThrowawayClient();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteClient", Map.of("realm", "acme", "clientId", clientId, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted client " + clientId + " in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(find(clientId) == null);
    }

    @Test
    void getServiceAccountShowsRolesAndGroups() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getServiceAccount", Map.of("realm", "acme", "clientId", "reports-service"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode account = readTree(text(response));
                    assertEquals("service-account-reports-service", account.get("username").asText(), account.toString());
                    assertEquals("[\"default-roles-acme\"]", account.at("/roles/realmRoles").toString());
                    List<String> effective = new ArrayList<>();
                    account.get("effectiveRealmRoles").forEach(role -> effective.add(role.asText()));
                    assertTrue(effective.containsAll(List.of("default-roles-acme", "offline_access")), effective.toString());
                    assertEquals(0, account.get("groups").size(), account.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getServiceAccountShowsGroupMembership() {
        String userId = keycloak.realm("acme").clients().get(find("reports-service").getId()).getServiceAccountUser().getId();
        String groupId = keycloak.realm("acme").getGroupByPath("/finance").getId();
        keycloak.realm("acme").users().get(userId).joinGroup(groupId);
        try {
            McpStreamableTestClient client = McpClients.connected();

            client.when()
                    .toolsCall("getServiceAccount", Map.of("realm", "acme", "clientId", "reports-service"), response -> {
                        assertFalse(response.isError(), text(response));
                        JsonNode account = readTree(text(response));
                        assertEquals(userId, account.get("userId").asText());
                        assertEquals("/finance", account.at("/groups/0/path").asText(), account.toString());
                        List<String> effective = new ArrayList<>();
                        account.get("effectiveRealmRoles").forEach(role -> effective.add(role.asText()));
                        assertTrue(effective.contains("viewer"), effective.toString());
                    })
                    .thenAssertResults();

            client.disconnect();
        } finally {
            keycloak.realm("acme").users().get(userId).leaveGroup(groupId);
        }
    }

    @Test
    void getServiceAccountRefusesClientWithout() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getServiceAccount", Map.of("realm", "acme", "clientId", "acme-web"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client acme-web has no service account in realm acme", text(response));
                })
                .toolsCall("getServiceAccount", Map.of("realm", "master", "clientId", "admin-cli"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client admin-cli has no service account in realm master", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }
}
