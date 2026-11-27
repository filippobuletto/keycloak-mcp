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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.github.filippobuletto.keycloakmcp.service.ClientScopeService;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClientScopeToolsTest {

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Creates an OIDC scope named tmp-scope-...; {@link #deleteThrowawayScopes} removes it. */
    String createThrowawayScope() {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName("tmp-scope-" + UUID.randomUUID());
        scope.setDescription("Throwaway");
        scope.setProtocol("openid-connect");
        scope.setAttributes(Map.of("include.in.token.scope", "false", "display.on.consent.screen", "true",
                "consent.screen.text", "Read your invoices", "gui.order", "7"));
        try (var response = acme().clientScopes().create(scope)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    /** One cleanup, so the order is fixed: clients first, then the scopes they may use. */
    @AfterEach
    void deleteThrowaways() {
        deleteThrowawayClients();
        deleteThrowawayScopes();
    }

    void deleteThrowawayScopes() {
        acme().clientScopes().findAll().stream()
                .filter(scope -> scope.getName().startsWith("tmp-scope-"))
                .forEach(scope -> acme().clientScopes().get(scope.getId()).remove());
    }

    @Test
    void listClientScopesMarksRealmDefaultsAndProtocol() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClientScopes", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    Map<String, JsonNode> byName = byName(readTree(text(response)));
                    for (String name : List.of("profile", "email", "roles")) {
                        assertEquals("default", byName.get(name).get("realmDefault").asText(), name);
                        assertEquals("openid-connect", byName.get(name).get("protocol").asText(), name);
                    }
                    assertEquals("optional", byName.get("offline_access").get("realmDefault").asText());
                    assertTrue(byName.values().stream().anyMatch(s -> s.get("protocol").asText().equals("saml")),
                            byName.keySet().toString());
                    assertFalse(byName.get("profile").get("id").asText().isBlank());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listClientScopesUnknownRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listClientScopes", Map.of("realm", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getBuiltInScopeWithMappers() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", "profile"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode scope = readTree(text(response));
                    assertEquals("profile", scope.get("name").asText());
                    assertEquals("openid-connect", scope.get("protocol").asText());
                    assertTrue(scope.get("includeInTokenScope").asBoolean());
                    assertTrue(scope.get("displayOnConsentScreen").asBoolean());
                    Map<String, String> mappers = mappers(scope);
                    assertEquals("oidc-usermodel-attribute-mapper", mappers.get("username"), mappers.toString());
                    assertEquals("oidc-full-name-mapper", mappers.get("full name"), mappers.toString());
                    assertEquals(0, scope.get("realmRoles").size());
                    assertEquals(0, scope.get("clientRoles").size());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getScopeWithSettingsAndRoleMappings() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        acme().clientScopes().get(id).getScopeMappings().realmLevel()
                .add(List.of(acme().roles().get("viewer").toRepresentation()));
        String reportsService = acme().clients().findByClientId("reports-service").get(0).getId();
        acme().clientScopes().get(id).getScopeMappings().clientLevel(reportsService)
                .add(List.of(acme().clients().get(reportsService).roles().get("report-reader").toRepresentation()));
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", name), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode scope = readTree(text(response));
                    assertEquals(id, scope.get("id").asText());
                    assertEquals("Throwaway", scope.get("description").asText());
                    assertFalse(scope.get("includeInTokenScope").asBoolean());
                    assertTrue(scope.get("displayOnConsentScreen").asBoolean());
                    assertEquals("Read your invoices", scope.get("consentScreenText").asText());
                    assertEquals("7", scope.get("guiOrder").asText());
                    assertEquals(0, scope.get("mappers").size());
                    assertEquals("viewer", scope.at("/realmRoles/0").asText());
                    assertEquals(1, scope.get("realmRoles").size());
                    assertEquals("report-reader", scope.at("/clientRoles/reports-service/0").asText());
                    assertEquals(1, scope.get("clientRoles").size());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientScopeUnknown() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client scope nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientClientScopesByName() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientClientScopes", Map.of("realm", "acme", "clientId", "acme-web"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode scopes = readTree(text(response));
                    assertEquals("acme-web", scopes.get("clientId").asText());
                    Set<String> defaults = names(scopes.get("defaultScopes"));
                    assertTrue(defaults.containsAll(Set.of("profile", "email", "roles")), defaults.toString());
                    Set<String> optional = names(scopes.get("optionalScopes"));
                    assertTrue(optional.contains("offline_access"), optional.toString());
                })
                .toolsCall("getClientClientScopes", Map.of("realm", "acme", "clientId", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void builtInListMatchesAFreshRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("tmp-scopes-" + UUID.randomUUID());
        realm.setEnabled(true);
        keycloak.realms().create(realm);
        try {
            Set<String> created = keycloak.realm(realm.getRealm()).clientScopes().findAll().stream()
                    .map(ClientScopeRepresentation::getName)
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(created, new TreeSet<>(ClientScopeService.BUILT_IN));
        } finally {
            keycloak.realm(realm.getRealm()).remove();
        }
    }

    /** A confidential acme client with service accounts, so a client credentials grant shows the scopes it gets. */
    String createThrowawayClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("tmp-scope-client-" + UUID.randomUUID());
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        acme().clients().create(client).close();
        return client.getClientId();
    }

    void deleteThrowawayClients() {
        acme().clients().findAll().stream()
                .filter(client -> client.getClientId().startsWith("tmp-scope-client-"))
                .forEach(client -> acme().clients().get(client.getId()).remove());
    }

    /** The {@code scope} claim of a client credentials token for an acme client, requesting {@code scope} if given. */
    String tokenScopeClaim(String clientId, String scope) throws Exception {
        String id = acme().clients().findByClientId(clientId).getFirst().getId();
        String secret = acme().clients().get(id).getSecret().getValue();
        String tokenUrl = ConfigProvider.getConfig().getValue("keycloak.url", String.class)
                + "/realms/acme/protocol/openid-connect/token";
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + (scope == null ? "" : "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            String token = readTree(response.body()).get("access_token").asText();
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            return readTree(payload).get("scope").asText();
        }
    }

    @Test
    void addAndRemoveClientScopeOnAClient() throws Exception {
        String clientId = createThrowawayClient();
        String name = "tmp-scope-billing-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();
        client.when()
                .toolsCall("createClientScope", Map.of("realm", "acme", "name", name), response -> {
                    assertFalse(response.isError(), text(response));
                })
                .thenAssertResults();

        client.when()
                .toolsCall("addClientScopeToClient", Map.of("realm", "acme", "clientId", clientId, "name", name, "type",
                        "optional"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode scopes = readTree(text(response));
                            assertTrue(names(scopes.get("optionalScopes")).contains(name), text(response));
                            assertFalse(names(scopes.get("defaultScopes")).contains(name), text(response));
                        })
                .thenAssertResults();
        assertTrue(List.of(tokenScopeClaim(clientId, name).split(" ")).contains(name));
        assertFalse(List.of(tokenScopeClaim(clientId, null).split(" ")).contains(name));

        client.when()
                .toolsCall("addClientScopeToClient", Map.of("realm", "acme", "clientId", clientId, "name", name, "type",
                        "default"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode scopes = readTree(text(response));
                            assertTrue(names(scopes.get("defaultScopes")).contains(name), text(response));
                            assertFalse(names(scopes.get("optionalScopes")).contains(name), text(response));
                        })
                .thenAssertResults();
        assertTrue(List.of(tokenScopeClaim(clientId, null).split(" ")).contains(name));

        for (int i = 0; i < 2; i++) {
            client.when()
                    .toolsCall("removeClientScopeFromClient", Map.of("realm", "acme", "clientId", clientId, "name",
                            name), response -> {
                                assertFalse(response.isError(), text(response));
                                JsonNode scopes = readTree(text(response));
                                assertFalse(names(scopes.get("defaultScopes")).contains(name), text(response));
                                assertFalse(names(scopes.get("optionalScopes")).contains(name), text(response));
                            })
                    .thenAssertResults();
        }

        client.disconnect();
        assertFalse(List.of(tokenScopeClaim(clientId, null).split(" ")).contains(name));
    }

    @Test
    void clientAssignmentRefusals() {
        String clientId = createThrowawayClient();
        ClientScopeRepresentation saml = new ClientScopeRepresentation();
        saml.setName("tmp-scope-saml-" + UUID.randomUUID());
        saml.setProtocol("saml");
        acme().clientScopes().create(saml).close();
        String adminCli = keycloak.realm("master").clients().findByClientId("admin-cli").getFirst().getId();
        var masterClient = keycloak.realm("master").clients().get(adminCli);
        List<String> masterBefore = List.of(names(masterClient.getDefaultClientScopes()).toString(),
                names(masterClient.getOptionalClientScopes()).toString());
        var acmeClient = acme().clients().get(acme().clients().findByClientId(clientId).getFirst().getId());
        List<String> acmeBefore = List.of(names(acmeClient.getDefaultClientScopes()).toString(),
                names(acmeClient.getOptionalClientScopes()).toString());
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addClientScopeToClient", Map.of("realm", "master", "clientId", "admin-cli", "name", "phone",
                        "type", "optional"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("removeClientScopeFromClient", Map.of("realm", "master", "clientId", "admin-cli", "name",
                        "roles"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("addClientScopeToClient", Map.of("realm", "acme", "clientId", clientId, "name",
                        saml.getName(), "type", "optional"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("removeClientScopeFromClient", Map.of("realm", "acme", "clientId", clientId, "name",
                        saml.getName()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("addClientScopeToClient", Map.of("realm", "acme", "clientId", clientId, "name", "phone",
                        "type", "sometimes"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("default or optional"), text(response));
                        })
                .toolsCall("addClientScopeToClient", Map.of("realm", "acme", "clientId", "nope", "name", "phone",
                        "type", "optional"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Client nope not found in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(masterBefore, List.of(names(masterClient.getDefaultClientScopes()).toString(),
                names(masterClient.getOptionalClientScopes()).toString()));
        assertEquals(acmeBefore, List.of(names(acmeClient.getDefaultClientScopes()).toString(),
                names(acmeClient.getOptionalClientScopes()).toString()));
    }

    /** The scope's place in acme's lists for new clients: default, optional or none. */
    String realmDefault(String id) {
        if (acme().getDefaultDefaultClientScopes().stream().anyMatch(s -> s.getId().equals(id))) {
            return "default";
        }
        return acme().getDefaultOptionalClientScopes().stream().anyMatch(s -> s.getId().equals(id)) ? "optional"
                : "none";
    }

    @Test
    void setRealmDefaultClientScopeAppliesToNewClientsOnly() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        String clientId = "tmp-scope-client-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "default",
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("default", readTree(text(response)).get("realmDefault").asText());
                        })
                .thenAssertResults();
        client.when()
                .toolsCall("listClientScopes", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("default", byName(readTree(text(response))).get(name).get("realmDefault").asText());
                })
                .thenAssertResults();
        client.when()
                .toolsCall("createClient", Map.of("realm", "acme", "clientId", clientId), response -> {
                    assertFalse(response.isError(), text(response));
                })
                .thenAssertResults();
        client.when()
                .toolsCall("getClientClientScopes", Map.of("realm", "acme", "clientId", clientId), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(names(readTree(text(response)).get("defaultScopes")).contains(name), text(response));
                })
                .thenAssertResults();
        client.when()
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "optional",
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                        })
                .thenAssertResults();
        assertEquals("optional", realmDefault(id));
        client.when()
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "none",
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("none", readTree(text(response)).get("realmDefault").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("none", realmDefault(id));
        String created = acme().clients().findByClientId(clientId).getFirst().getId();
        assertTrue(acme().clients().get(created).getDefaultClientScopes().stream().anyMatch(s -> s.getId().equals(id)),
                "an existing client keeps the scope");
    }

    @Test
    void setRealmDefaultClientScopeRefusals() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        ClientScopeRepresentation saml = new ClientScopeRepresentation();
        saml.setName("tmp-scope-saml-" + UUID.randomUUID());
        saml.setProtocol("saml");
        String samlId = CreatedResponseUtil.getCreatedId(acme().clientScopes().create(saml));
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "default"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("confirm=true"), text(response));
                        })
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "master", "name", "phone", "type", "none",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", saml.getName(), "type",
                        "default", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "always",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("default, optional or none"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("none", realmDefault(id));
        assertEquals("none", realmDefault(samlId));
        assertTrue(keycloak.realm("master").getDefaultOptionalClientScopes().stream()
                .anyMatch(s -> s.getName().equals("phone")));
    }

    @Test
    void addAndRemoveClientScopeRoles() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        String clientId = createThrowawayClient();
        String clientUuid = acme().clients().findByClientId(clientId).getFirst().getId();
        RoleRepresentation role = new RoleRepresentation();
        role.setName("invoice-reader");
        acme().clients().get(clientUuid).roles().create(role);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", name, "role", "viewer"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("viewer", readTree(text(response)).at("/realmRoles/0").asText(), text(response));
                })
                .thenAssertResults();
        client.when()
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", name, "role", "invoice-reader",
                        "clientId", clientId), response -> {
                            assertFalse(response.isError(), text(response));
                        })
                .thenAssertResults();
        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", name), response -> {
                    JsonNode scope = readTree(text(response));
                    assertEquals(List.of("viewer"), List.of(scope.at("/realmRoles/0").asText()));
                    assertEquals(1, scope.get("realmRoles").size(), text(response));
                    assertEquals("invoice-reader", scope.get("clientRoles").get(clientId).get(0).asText(),
                            text(response));
                })
                .thenAssertResults();
        client.when()
                .toolsCall("removeClientScopeRole", Map.of("realm", "acme", "name", name, "role", "viewer"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                        })
                .thenAssertResults();
        client.when()
                .toolsCall("removeClientScopeRole", Map.of("realm", "acme", "name", name, "role", "invoice-reader",
                        "clientId", clientId), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode scope = readTree(text(response));
                            assertEquals(0, scope.get("realmRoles").size(), text(response));
                            assertEquals(0, scope.get("clientRoles").size(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        var mappings = acme().clientScopes().get(id).getScopeMappings().getAll();
        assertTrue(mappings.getRealmMappings() == null || mappings.getRealmMappings().isEmpty());
        assertTrue(mappings.getClientMappings() == null || mappings.getClientMappings().isEmpty());
    }

    @Test
    void clientScopeRoleRefusals() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        ClientScopeRepresentation saml = new ClientScopeRepresentation();
        saml.setName("tmp-scope-saml-" + UUID.randomUUID());
        saml.setProtocol("saml");
        acme().clientScopes().create(saml).close();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", name, "role", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Role nope not found in realm acme", text(response));
                })
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", name, "role", "nope", "clientId",
                        "reports-service"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Role nope not found in client reports-service of realm acme",
                                    text(response));
                        })
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", name, "role", "viewer", "clientId",
                        "nope"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Client nope not found in realm acme", text(response));
                        })
                .toolsCall("addClientScopeRole", Map.of("realm", "master", "name", "roles", "role", "admin"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("removeClientScopeRole", Map.of("realm", "master", "name", "roles", "role", "admin"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("addClientScopeRole", Map.of("realm", "acme", "name", saml.getName(), "role", "viewer"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("removeClientScopeRole", Map.of("realm", "acme", "name", saml.getName(), "role",
                        "viewer"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        var mappings = acme().clientScopes().get(id).getScopeMappings().getAll();
        assertTrue(mappings.getRealmMappings() == null || mappings.getRealmMappings().isEmpty());
        assertTrue(mappings.getClientMappings() == null || mappings.getClientMappings().isEmpty());
        String roles = keycloak.realm("master").clientScopes().findAll().stream()
                .filter(s -> s.getName().equals("roles")).findFirst().orElseThrow().getId();
        var masterRealmMappings = keycloak.realm("master").clientScopes().get(roles).getScopeMappings().getAll()
                .getRealmMappings();
        assertTrue(masterRealmMappings == null
                || masterRealmMappings.stream().noneMatch(r -> r.getName().equals("admin")));
    }

    ClientScopeRepresentation findScope(String name) {
        return acme().clientScopes().findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
    }

    @Test
    void createClientScopeRoundTripsEveryField() {
        String name = "tmp-scope-billing-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClientScope", Map.of("realm", "acme", "name", name, "description", "Billing",
                        "includeInTokenScope", false, "displayOnConsentScreen", false, "consentScreenText",
                        "Read your invoices"), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals(name, readTree(text(response)).get("name").asText());
                        })
                .thenAssertResults();
        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", name), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode scope = readTree(text(response));
                    assertEquals("Billing", scope.get("description").asText());
                    assertEquals("openid-connect", scope.get("protocol").asText());
                    assertFalse(scope.get("includeInTokenScope").asBoolean());
                    assertFalse(scope.get("displayOnConsentScreen").asBoolean());
                    assertEquals("Read your invoices", scope.get("consentScreenText").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createClientScopeDefaultsToIncludedAndShownOnConsent() {
        String name = "tmp-scope-" + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClientScope", Map.of("realm", "acme", "name", name), response -> {
                    assertFalse(response.isError(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        Map<String, String> attributes = findScope(name).getAttributes();
        assertEquals("true", attributes.get("include.in.token.scope"), attributes.toString());
        assertEquals("true", attributes.get("display.on.consent.screen"), attributes.toString());
        assertEquals("openid-connect", findScope(name).getProtocol());
    }

    @Test
    void createClientScopeDuplicate() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClientScope", Map.of("realm", "acme", "name", "profile"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("409"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void updateClientScopeChangesOnlySuppliedFields() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateClientScope", Map.of("realm", "acme", "name", name, "consentScreenText",
                        "Read and pay your invoices"), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Read and pay your invoices",
                                    readTree(text(response)).get("consentScreenText").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        ClientScopeRepresentation updated = acme().clientScopes().get(id).toRepresentation();
        assertEquals("Throwaway", updated.getDescription());
        assertEquals("false", updated.getAttributes().get("include.in.token.scope"));
        assertEquals("true", updated.getAttributes().get("display.on.consent.screen"));
        assertEquals("7", updated.getAttributes().get("gui.order"));
        assertEquals("Read and pay your invoices", updated.getAttributes().get("consent.screen.text"));
    }

    @Test
    void deleteClientScopeAssignedToAClientAndRealmDefault() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        String acmeWeb = acme().clients().findByClientId("acme-web").get(0).getId();
        acme().clients().get(acmeWeb).addOptionalClientScope(id);
        acme().addDefaultDefaultClientScope(id);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteClientScope", Map.of("realm", "acme", "name", name, "confirm", true), response -> {
                    assertFalse(response.isError(), text(response));
                    assertEquals("Deleted client scope " + name + " in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(findScope(name) == null);
        assertTrue(acme().clients().get(acmeWeb).getOptionalClientScopes().stream()
                .noneMatch(s -> s.getId().equals(id)));
        assertTrue(acme().getDefaultDefaultClientScopes().stream().noneMatch(s -> s.getId().equals(id)));
    }

    @Test
    void deleteClientScopeRefusesWithoutConfirm() {
        String id = createThrowawayScope();
        String name = acme().clientScopes().get(id).toRepresentation().getName();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteClientScope", Map.of("realm", "acme", "name", name), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("confirm=true"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(findScope(name) != null);
    }

    @Test
    void deleteClientScopeRefusesBuiltIn() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteClientScope", Map.of("realm", "acme", "name", "roles", "confirm", true), response -> {
                    assertTrue(response.isError());
                    assertEquals("Client scope roles is built into Keycloak, and deleting it would change every token"
                            + " in realm acme. Nothing was changed.", text(response));
                })
                .thenAssertResults();

        client.disconnect();
        assertTrue(findScope("roles") != null);
    }

    @Test
    void scopeWritesRefuseSamlScopes() {
        ClientScopeRepresentation saml = new ClientScopeRepresentation();
        saml.setName("tmp-scope-saml-" + UUID.randomUUID());
        saml.setDescription("SAML throwaway");
        saml.setProtocol("saml");
        acme().clientScopes().create(saml).close();
        String expected = "Client scope " + saml.getName()
                + " uses protocol saml; only openid-connect scopes can be changed. Nothing was changed.";
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateClientScope", Map.of("realm", "acme", "name", saml.getName(), "description", "x"),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals(expected, text(response));
                        })
                .toolsCall("deleteClientScope", Map.of("realm", "acme", "name", saml.getName(), "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals(expected, text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("SAML throwaway", findScope(saml.getName()).getDescription());
    }

    @Test
    void scopeWritesRefuseMasterRealm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createClientScope", Map.of("realm", "master", "name", "tmp-scope-master"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).contains("refused for the master realm"), text(response));
                })
                .toolsCall("updateClientScope", Map.of("realm", "master", "name", "profile", "description", "x"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("deleteClientScope", Map.of("realm", "master", "name", "phone", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        var master = keycloak.realm("master").clientScopes().findAll();
        assertTrue(master.stream().noneMatch(s -> s.getName().equals("tmp-scope-master")));
        assertTrue(master.stream().anyMatch(s -> s.getName().equals("phone")));
        assertTrue(master.stream().noneMatch(s -> "x".equals(s.getDescription())));
    }

    private static Map<String, JsonNode> byName(JsonNode scopes) {
        Map<String, JsonNode> byName = new HashMap<>();
        scopes.forEach(scope -> byName.put(scope.get("name").asText(), scope));
        return byName;
    }

    private static Map<String, String> mappers(JsonNode scope) {
        Map<String, String> mappers = new HashMap<>();
        scope.get("mappers").forEach(m -> mappers.put(m.get("name").asText(), m.get("type").asText()));
        return mappers;
    }

    private static Set<String> names(List<ClientScopeRepresentation> scopes) {
        return scopes.stream().map(ClientScopeRepresentation::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> names(JsonNode array) {
        Set<String> names = new TreeSet<>();
        array.forEach(n -> names.add(n.asText()));
        return names;
    }
}
