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

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClientScopeMapperToolsTest {

    static final Map<String, String> TENANT = Map.of("claim.name", "tenant", "claim.value", "acme",
            "access.token.claim", "true", "jsonType.label", "String");

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Creates an OIDC scope named tmp-scope-... with the given protocol; {@link #deleteThrowaways} removes it. */
    String createThrowawayScope(String protocol) {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName("tmp-scope-" + UUID.randomUUID());
        scope.setProtocol(protocol);
        try (var response = acme().clientScopes().create(scope)) {
            CreatedResponseUtil.getCreatedId(response);
        }
        return scope.getName();
    }

    /** A confidential acme client with service accounts that has {@code scope} as a default scope. */
    String createThrowawayClientWith(String scope) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("tmp-scope-client-" + UUID.randomUUID());
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        String id;
        try (var response = acme().clients().create(client)) {
            id = CreatedResponseUtil.getCreatedId(response);
        }
        String scopeId = acme().clientScopes().findAll().stream().filter(s -> s.getName().equals(scope))
                .findFirst().orElseThrow().getId();
        acme().clients().get(id).addDefaultClientScope(scopeId);
        return client.getClientId();
    }

    @AfterEach
    void deleteThrowaways() {
        acme().clients().findAll().stream()
                .filter(client -> client.getClientId().startsWith("tmp-scope-client-"))
                .forEach(client -> acme().clients().get(client.getId()).remove());
        acme().clientScopes().findAll().stream()
                .filter(scope -> scope.getName().startsWith("tmp-scope-"))
                .forEach(scope -> acme().clientScopes().get(scope.getId()).remove());
    }

    List<ProtocolMapperRepresentation> mappersOf(String scope) {
        List<ProtocolMapperRepresentation> mappers = acme().clientScopes().findAll().stream()
                .filter(s -> s.getName().equals(scope)).findFirst().orElseThrow().getProtocolMappers();
        return mappers == null ? List.of() : mappers;
    }

    /** The decoded access token payload of a client credentials grant for an acme client. */
    JsonNode accessToken(String clientId) throws Exception {
        String id = acme().clients().findByClientId(clientId).getFirst().getId();
        String secret = acme().clients().get(id).getSecret().getValue();
        String tokenUrl = ConfigProvider.getConfig().getValue("keycloak.url", String.class)
                + "/realms/acme/protocol/openid-connect/token";
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            String token = readTree(response.body()).get("access_token").asText();
            return readTree(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8));
        }
    }

    /** Adds the tenant mapper through the admin client, plus the legacy key built-in role mappers hold. */
    void addTenantMapperWithLegacyKey(String scope) {
        String scopeId = acme().clientScopes().findAll().stream().filter(s -> s.getName().equals(scope))
                .findFirst().orElseThrow().getId();
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("tenant");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        Map<String, String> config = new HashMap<>(TENANT);
        config.put("user.attribute", "legacy");
        mapper.setConfig(config);
        acme().clientScopes().get(scopeId).getProtocolMappers().createMapper(mapper).close();
    }

    static JsonNode mapperNamed(JsonNode scope, String name) {
        for (JsonNode mapper : scope.get("mappers")) {
            if (mapper.get("name").asText().equals(name)) {
                return mapper;
            }
        }
        return null;
    }

    @Test
    void listProtocolMapperTypesShowsOidcTypesWithConfigKeys() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listProtocolMapperTypes", Map.of(), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode types = readTree(text(response));
                    JsonNode hardcoded = null;
                    for (JsonNode type : types) {
                        assertFalse(type.get("id").asText().startsWith("saml-"), type.get("id").asText());
                        if (type.get("id").asText().equals("oidc-hardcoded-claim-mapper")) {
                            hardcoded = type;
                        }
                    }
                    assertTrue(hardcoded != null, "oidc-hardcoded-claim-mapper missing");
                    assertFalse(hardcoded.get("name").asText().isBlank());
                    Set<String> keys = new TreeSet<>();
                    hardcoded.get("properties").forEach(p -> keys.add(p.get("key").asText()));
                    assertTrue(keys.containsAll(List.of("claim.name", "claim.value", "access.token.claim")),
                            keys.toString());
                    Map<String, JsonNode> byKey = new java.util.HashMap<>();
                    hardcoded.get("properties").forEach(p -> byKey.put(p.get("key").asText(), p));
                    // Keycloak sends the message key tokenClaimName.label; the admin console bundle resolves it
                    assertEquals("Token Claim Name", byKey.get("claim.name").get("label").asText());
                    assertTrue(byKey.get("claim.name").get("required").asBoolean());
                    assertFalse(byKey.get("claim.value").get("required").asBoolean());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getClientScopeShowsMapperConfig() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getClientScope", Map.of("realm", "acme", "name", "email"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode email = null;
                    for (JsonNode mapper : readTree(text(response)).get("mappers")) {
                        if (mapper.get("name").asText().equals("email")) {
                            email = mapper;
                        }
                    }
                    assertTrue(email != null, text(response));
                    assertEquals("email", email.get("config").get("claim.name").asText(), email.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void addClientScopeMapperPutsTheClaimInTokens() throws Exception {
        String scope = createThrowawayScope("openid-connect");
        String clientId = createThrowawayClientWith(scope);
        assertTrue(accessToken(clientId).get("tenant") == null);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode mapper = mapperNamed(readTree(text(response)), "tenant");
                            assertTrue(mapper != null, text(response));
                            assertEquals("oidc-hardcoded-claim-mapper", mapper.get("type").asText());
                            assertEquals("acme", mapper.get("config").get("claim.value").asText(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("acme", accessToken(clientId).get("tenant").asText());
    }

    @Test
    void addClientScopeMapperRefusals() {
        String scope = createThrowawayScope("openid-connect");
        String saml = createThrowawayScope("saml");
        McpStreamableTestClient client = McpClients.connected();
        client.when()
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertFalse(response.isError(), text(response));
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("409"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "other",
                        "mapperType", "oidc-no-such-mapper", "config", TENANT), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("oidc-no-such-mapper"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "other",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", Map.of("claim.nmae", "x")),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("claim.nmae"), text(response));
                            assertTrue(text(response).contains("claim.name"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "other",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", Map.of("claim.value", "x")),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("claim.name is required"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", saml, "name", "other",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "master", "scope", "profile", "name", "other",
                        "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("addClientScopeMapper", Map.of("realm", "acme", "scope", "tmp-scope-nope", "name",
                        "other", "mapperType", "oidc-hardcoded-claim-mapper", "config", TENANT), response -> {
                            assertTrue(response.isError());
                            assertEquals("Client scope tmp-scope-nope not found in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(List.of("tenant"), mappersOf(scope).stream().map(ProtocolMapperRepresentation::getName).toList());
        assertEquals(List.of(), mappersOf(saml));
        assertTrue(keycloak.realm("master").clientScopes().findAll().stream()
                .filter(s -> s.getName().equals("profile")).findFirst().orElseThrow().getProtocolMappers().stream()
                .noneMatch(m -> m.getName().equals("other")));
    }

    @Test
    void updateAndDeleteClientScopeMapperChangeTheToken() throws Exception {
        String scope = createThrowawayScope("openid-connect");
        String clientId = createThrowawayClientWith(scope);
        addTenantMapperWithLegacyKey(scope);
        assertEquals("acme", accessToken(clientId).get("tenant").asText());
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "config", Map.of("claim.value", "globex")), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode config = mapperNamed(readTree(text(response)), "tenant").get("config");
                            assertEquals("globex", config.get("claim.value").asText(), text(response));
                            assertEquals("tenant", config.get("claim.name").asText(), text(response));
                            assertEquals("true", config.get("access.token.claim").asText(), text(response));
                            assertEquals("String", config.get("jsonType.label").asText(), text(response));
                            assertEquals("legacy", config.get("user.attribute").asText(), text(response));
                        })
                .thenAssertResults();
        assertEquals("globex", accessToken(clientId).get("tenant").asText());

        client.when()
                .toolsCall("deleteClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("confirm=true"), text(response));
                        })
                .thenAssertResults();
        assertEquals(List.of("tenant"), mappersOf(scope).stream().map(ProtocolMapperRepresentation::getName).toList());

        client.when()
                .toolsCall("deleteClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(List.of(), mappersOf(scope));
        assertTrue(accessToken(clientId).get("tenant") == null);
    }

    @Test
    void updateAndDeleteClientScopeMapperRefusals() {
        String scope = createThrowawayScope("openid-connect");
        String saml = createThrowawayScope("saml");
        addTenantMapperWithLegacyKey(scope);
        Map<String, String> before = mappersOf(scope).getFirst().getConfig();
        String notFound = "Protocol mapper nope not found in client scope " + scope + " of realm acme";
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "nope",
                        "config", Map.of("claim.value", "x")), response -> {
                            assertTrue(response.isError());
                            assertEquals(notFound, text(response));
                        })
                .toolsCall("deleteClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "nope",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals(notFound, text(response));
                        })
                .toolsCall("updateClientScopeMapper", Map.of("realm", "acme", "scope", scope, "name", "tenant",
                        "config", Map.of("claim.value", "x", "claim.nmae", "x")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("claim.nmae"), text(response));
                        })
                .toolsCall("updateClientScopeMapper", Map.of("realm", "acme", "scope", saml, "name", "tenant",
                        "config", Map.of("claim.value", "x")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("deleteClientScopeMapper", Map.of("realm", "acme", "scope", saml, "name", "tenant",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("uses protocol saml"), text(response));
                        })
                .toolsCall("updateClientScopeMapper", Map.of("realm", "master", "scope", "email", "name", "email",
                        "config", Map.of("claim.name", "x")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .toolsCall("deleteClientScopeMapper", Map.of("realm", "master", "scope", "email", "name", "email",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(before, mappersOf(scope).getFirst().getConfig());
        assertTrue(keycloak.realm("master").clientScopes().findAll().stream()
                .filter(s -> s.getName().equals("email")).findFirst().orElseThrow().getProtocolMappers().stream()
                .anyMatch(m -> m.getName().equals("email") && "email".equals(m.getConfig().get("claim.name"))));
    }
}
