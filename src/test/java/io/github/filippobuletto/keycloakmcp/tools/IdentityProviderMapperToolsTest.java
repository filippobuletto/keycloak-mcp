package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.IdentityProviderToolsTest.withField;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class IdentityProviderMapperToolsTest {

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Creates a provider aliased tmp-idpm-... through the admin client; {@link #deleteThrowaways} removes it. */
    String createThrowawayProvider(String providerId) {
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias("tmp-idpm-" + UUID.randomUUID());
        provider.setProviderId(providerId);
        provider.setConfig(new HashMap<>(Map.of("clientId", "acme-broker", "authorizationUrl",
                "https://idp.example/auth", "tokenUrl", "https://idp.example/token", "singleSignOnServiceUrl",
                "https://idp.example/sso")));
        acme().identityProviders().create(provider).close();
        return provider.getAlias();
    }

    /** Adds a mapper through the admin client, bypassing the tools' checks. */
    void addStoredMapper(String alias, String name, String type, Map<String, String> config) {
        IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
        mapper.setName(name);
        mapper.setIdentityProviderAlias(alias);
        mapper.setIdentityProviderMapper(type);
        mapper.setConfig(new HashMap<>(config));
        acme().identityProviders().get(alias).addMapper(mapper).close();
    }

    @AfterEach
    void deleteThrowaways() {
        // Deleting a provider deletes its mappers
        acme().identityProviders().findAll().stream()
                .filter(provider -> provider.getAlias().startsWith("tmp-idpm-"))
                .forEach(provider -> acme().identityProviders().get(provider.getAlias()).remove());
    }

    @Test
    void listIdentityProviderMapperTypesFitsTheProviderType() {
        String oidc = createThrowawayProvider("oidc");
        String saml = createThrowawayProvider("saml");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listIdentityProviderMapperTypes", Map.of("realm", "acme", "alias", oidc), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode types = readTree(text(response));
                    JsonNode attribute = withField(types, "id", "oidc-user-attribute-idp-mapper");
                    assertNotNull(attribute, text(response));
                    assertEquals("Attribute Importer", attribute.get("category").asText());
                    JsonNode claim = withField(attribute.get("properties"), "key", "claim");
                    assertNotNull(claim, text(response));
                    assertEquals("Claim", claim.get("label").asText());
                    assertNotNull(withField(attribute.get("properties"), "key", "user.attribute"), text(response));
                    assertNotNull(withField(types, "id", "hardcoded-attribute-idp-mapper"), text(response));
                    for (JsonNode type : types) {
                        assertFalse(type.get("id").asText().startsWith("saml-"), text(response));
                    }
                })
                .toolsCall("listIdentityProviderMapperTypes", Map.of("realm", "acme", "alias", saml), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode types = readTree(text(response));
                    assertNotNull(withField(types, "id", "saml-user-attribute-idp-mapper"), text(response));
                    assertNull(withField(types, "id", "oidc-user-attribute-idp-mapper"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listIdentityProviderMapperTypesNamesAnUnknownAlias() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listIdentityProviderMapperTypes", Map.of("realm", "acme", "alias", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Identity provider nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getIdentityProviderShowsMappersWithTheirSyncMode() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "department", "oidc-user-attribute-idp-mapper",
                Map.of("claim", "dept", "user.attribute", "department", "syncMode", "FORCE"));
        addStoredMapper(alias, "attr-legacy", "hardcoded-attribute-idp-mapper",
                Map.of("attribute", "tenant", "attribute.value", "acme"));
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getIdentityProvider", Map.of("realm", "acme", "alias", alias), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode mappers = readTree(text(response)).get("mappers");
                    assertNotNull(mappers, text(response));
                    assertEquals(2, mappers.size(), text(response));
                    // Sorted by name
                    assertEquals("attr-legacy", mappers.get(0).get("name").asText());
                    JsonNode department = mappers.get(1);
                    assertEquals("department", department.get("name").asText());
                    assertEquals("oidc-user-attribute-idp-mapper", department.get("type").asText());
                    assertEquals("FORCE", department.get("syncMode").asText());
                    assertEquals("dept", department.get("config").get("claim").asText());
                    assertEquals("department", department.get("config").get("user.attribute").asText());
                    assertFalse(department.get("config").has("syncMode"), text(response));
                    // Keycloak runs a mapper without syncMode as LEGACY
                    JsonNode legacy = mappers.get(0);
                    assertEquals("LEGACY", legacy.get("syncMode").asText());
                    assertEquals("acme", legacy.get("config").get("attribute.value").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    static final Map<String, String> ATTRIBUTE_CONFIG = Map.of("attribute", "tenant", "attribute.value", "acme");

    List<IdentityProviderMapperRepresentation> stored(String alias) {
        return acme().identityProviders().get(alias).getMappers();
    }

    @Test
    void addIdentityProviderMapperStoresTheConfigWithSyncModeInherit() {
        String alias = createThrowawayProvider("oidc");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", ATTRIBUTE_CONFIG), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode mapper = withField(readTree(text(response)).get("mappers"), "name", "tenant");
                            assertNotNull(mapper, text(response));
                            assertEquals("hardcoded-attribute-idp-mapper", mapper.get("type").asText());
                            assertEquals("INHERIT", mapper.get("syncMode").asText());
                            assertEquals("tenant", mapper.get("config").get("attribute").asText());
                            assertEquals("acme", mapper.get("config").get("attribute.value").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("INHERIT", stored(alias).getFirst().getConfig().get("syncMode"));
    }

    @Test
    void addIdentityProviderMapperTakesASyncMode() {
        String alias = createThrowawayProvider("oidc");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", ATTRIBUTE_CONFIG, "syncMode",
                        "FORCE"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode mapper = withField(readTree(text(response)).get("mappers"), "name", "tenant");
                            assertEquals("FORCE", mapper.get("syncMode").asText(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void addIdentityProviderMapperRefusesTypesTheProviderCannotUse() {
        String alias = createThrowawayProvider("oidc");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "m",
                        "mapperType", "nope", "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Mapper type nope cannot be used by identity provider "
                                    + alias + " of realm acme; valid types: "), text(response));
                            assertTrue(text(response).contains("oidc-user-attribute-idp-mapper"), text(response));
                        })
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "m",
                        "mapperType", "saml-user-attribute-idp-mapper", "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Mapper type saml-user-attribute-idp-mapper cannot be"
                                    + " used by identity provider " + alias), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored(alias).isEmpty(), stored(alias).toString());
    }

    @Test
    void addIdentityProviderMapperRefusesUndeclaredAndSyncModeKeys() {
        String alias = createThrowawayProvider("oidc");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "m",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", Map.of("atribute", "tenant")),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Config key atribute is not declared by mapper type"
                                    + " hardcoded-attribute-idp-mapper; valid keys: attribute, attribute.value."
                                    + " Nothing was changed.", text(response));
                        })
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "m",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", Map.of("syncMode", "FORCE")),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Set the sync mode with the syncMode parameter, not as a config key."
                                    + " Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored(alias).isEmpty(), stored(alias).toString());
    }

    @Test
    void addIdentityProviderMapperOffersOnlyTheFourSyncModes() {
        String alias = createThrowawayProvider("oidc");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsList(page -> {
                    String schema = page.tools().stream()
                            .filter(tool -> tool.name().equals("addIdentityProviderMapper"))
                            .findFirst().orElseThrow().inputSchema().toString();
                    for (String mode : List.of("INHERIT", "LEGACY", "IMPORT", "FORCE")) {
                        assertTrue(schema.contains(mode), schema);
                    }
                })
                .thenAssertResults();
        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "m",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", ATTRIBUTE_CONFIG, "syncMode",
                        "BOGUS"), response -> assertTrue(response.isError(), text(response)))
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored(alias).isEmpty(), stored(alias).toString());
    }

    @Test
    void addIdentityProviderMapperPassesKeycloaksRefusalOfADuplicateName() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "tenant", "hardcoded-attribute-idp-mapper", ATTRIBUTE_CONFIG);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", ATTRIBUTE_CONFIG), response -> {
                            assertTrue(response.isError());
                            assertEquals("Keycloak returned 400 Bad Request: Failed to add mapper 'tenant' to"
                                    + " identity provider [oidc].", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(1, stored(alias).size());
    }

    Map<String, String> storedConfig(String alias, String name) {
        return stored(alias).stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow().getConfig();
    }

    @Test
    void updateIdentityProviderMapperChangesOnlyTheSuppliedKey() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "tenant", "hardcoded-attribute-idp-mapper",
                Map.of("attribute", "tenant", "attribute.value", "acme", "syncMode", "FORCE"));
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "config", Map.of("attribute.value", "globex")), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode mapper = withField(readTree(text(response)).get("mappers"), "name", "tenant");
                            assertEquals("globex", mapper.get("config").get("attribute.value").asText());
                            assertEquals("tenant", mapper.get("config").get("attribute").asText());
                            assertEquals("FORCE", mapper.get("syncMode").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of("attribute", "tenant", "attribute.value", "globex", "syncMode", "FORCE"),
                storedConfig(alias, "tenant"));
    }

    @Test
    void updateIdentityProviderMapperChangesOnlyTheSyncMode() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "tenant", "hardcoded-attribute-idp-mapper", ATTRIBUTE_CONFIG);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "syncMode", "IMPORT"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode mapper = withField(readTree(text(response)).get("mappers"), "name", "tenant");
                            assertEquals("IMPORT", mapper.get("syncMode").asText(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of("attribute", "tenant", "attribute.value", "acme", "syncMode", "IMPORT"),
                storedConfig(alias, "tenant"));
    }

    @Test
    void updateIdentityProviderMapperRefusesAnUndeclaredKey() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "tenant", "hardcoded-attribute-idp-mapper", ATTRIBUTE_CONFIG);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "config", Map.of("attribute.value", "globex", "atribute", "x")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Config key atribute is not declared by mapper type"
                                    + " hardcoded-attribute-idp-mapper"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(ATTRIBUTE_CONFIG, storedConfig(alias, "tenant"));
    }

    @Test
    void deleteIdentityProviderMapperNeedsConfirm() {
        String alias = createThrowawayProvider("oidc");
        addStoredMapper(alias, "tenant", "hardcoded-attribute-idp-mapper", ATTRIBUTE_CONFIG);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "confirm", false), response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertEquals(1, stored(alias).size());

        client.when()
                .toolsCall("deleteIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "tenant",
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Deleted mapper tenant from identity provider " + alias + " in realm acme",
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored(alias).isEmpty(), stored(alias).toString());
    }

    @Test
    void updateAndDeleteNameAnUnknownMapper() {
        String alias = createThrowawayProvider("oidc");
        String expected = "Identity provider mapper nope not found in identity provider " + alias + " of realm acme";
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "nope",
                        "syncMode", "FORCE"), response -> {
                            assertTrue(response.isError());
                            assertEquals(expected, text(response));
                        })
                .toolsCall("deleteIdentityProviderMapper", Map.of("realm", "acme", "alias", alias, "name", "nope",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals(expected, text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void updateAndDeleteAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProviderMapper", Map.of("realm", "master", "alias", "any", "name", "m",
                        "syncMode", "FORCE"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "updateIdentityProviderMapper is refused for the master realm"), text(response));
                        })
                .toolsCall("deleteIdentityProviderMapper", Map.of("realm", "master", "alias", "any", "name", "m",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "deleteIdentityProviderMapper is refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void addIdentityProviderMapperNamesAnUnknownAliasAndRefusesMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "acme", "alias", "nope", "name", "m",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertEquals("Identity provider nope not found in realm acme", text(response));
                        })
                .toolsCall("addIdentityProviderMapper", Map.of("realm", "master", "alias", "any", "name", "m",
                        "mapperType", "hardcoded-attribute-idp-mapper", "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "addIdentityProviderMapper is refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }
}
