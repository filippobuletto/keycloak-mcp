package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class IdentityProviderToolsTest {

    static final String SECRET = "s3cret-value";

    static final Map<String, String> OIDC_CONFIG = Map.of("clientId", "acme-broker", "clientSecret", SECRET,
            "clientAuthMethod", "client_secret_post", "authorizationUrl", "https://idp.example/auth", "tokenUrl",
            "https://idp.example/token");

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Creates an oidc provider aliased tmp-idp-... through the admin client; {@link #deleteThrowaways} removes it. */
    String createThrowawayProvider() {
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias("tmp-idp-" + UUID.randomUUID());
        provider.setDisplayName("Throwaway");
        provider.setProviderId("oidc");
        provider.setTrustEmail(true);
        provider.setConfig(new HashMap<>(OIDC_CONFIG));
        acme().identityProviders().create(provider).close();
        return provider.getAlias();
    }

    @AfterEach
    void deleteThrowaways() {
        acme().identityProviders().findAll().stream()
                .filter(provider -> provider.getAlias().startsWith("tmp-idp-"))
                .forEach(provider -> acme().identityProviders().get(provider.getAlias()).remove());
    }

    static JsonNode withField(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (item.get(field).asText().equals(value)) {
                return item;
            }
        }
        return null;
    }

    @Test
    void listIdentityProviderTypesMarksSocialTypes() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listIdentityProviderTypes", Map.of(), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode types = readTree(text(response));
                    JsonNode oidc = withField(types, "id", "oidc");
                    JsonNode google = withField(types, "id", "google");
                    assertNotNull(oidc, text(response));
                    assertNotNull(withField(types, "id", "saml"), text(response));
                    assertNotNull(google, text(response));
                    assertFalse(oidc.get("social").asBoolean());
                    assertTrue(google.get("social").asBoolean());
                    assertFalse(google.get("name").asText().isBlank());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listIdentityProvidersShowsAThrowawayProvider() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listIdentityProviders", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode provider = withField(readTree(text(response)), "alias", alias);
                    assertNotNull(provider, text(response));
                    assertEquals("Throwaway", provider.get("displayName").asText());
                    assertEquals("oidc", provider.get("providerId").asText());
                    assertTrue(provider.get("enabled").asBoolean());
                    assertFalse(text(response).contains(SECRET));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getIdentityProviderShowsFieldsAndConfigWithTheSecretMasked() {
        String alias = createThrowawayProvider();
        assertEquals(null, stored(alias).getFirstBrokerLoginFlowAlias());
        String realmFirstBrokerLoginFlow = acme().toRepresentation().getFirstBrokerLoginFlow();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getIdentityProvider", Map.of("realm", "acme", "alias", alias), response -> {
                    assertFalse(response.isError(), text(response));
                    assertFalse(text(response).contains(SECRET), text(response));
                    JsonNode provider = readTree(text(response));
                    assertEquals(alias, provider.get("alias").asText());
                    assertEquals("Throwaway", provider.get("displayName").asText());
                    assertEquals("oidc", provider.get("providerId").asText());
                    assertTrue(provider.get("enabled").asBoolean());
                    assertTrue(provider.get("trustEmail").asBoolean());
                    assertFalse(provider.get("storeToken").asBoolean());
                    assertFalse(provider.get("linkOnly").asBoolean());
                    assertFalse(provider.get("hideOnLogin").asBoolean());
                    // The provider sets no flow, so Keycloak runs the realm's first broker login flow
                    assertEquals(realmFirstBrokerLoginFlow, provider.get("firstBrokerLoginFlowAlias").asText(),
                            text(response));
                    assertTrue(provider.get("firstBrokerLoginFlowFromRealm").asBoolean(), text(response));
                    assertTrue(provider.has("postBrokerLoginFlowAlias"), text(response));
                    JsonNode config = provider.get("config");
                    assertEquals("**********", config.get("clientSecret").asText());
                    assertEquals("acme-broker", config.get("clientId").asText());
                    assertEquals("https://idp.example/token", config.get("tokenUrl").asText());
                    assertEquals("https://idp.example/auth", config.get("authorizationUrl").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    static String throwawayAlias() {
        return "tmp-idp-" + UUID.randomUUID();
    }

    boolean exists(String realm, String alias) {
        return keycloak.realm(realm).identityProviders().findAll().stream()
                .anyMatch(provider -> provider.getAlias().equals(alias));
    }

    @Test
    void createIdentityProviderStoresFieldsAndConfigAndMasksTheSecret() {
        String alias = throwawayAlias();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createIdentityProvider", Map.of("realm", "acme", "alias", alias, "providerId", "oidc",
                        "config", OIDC_CONFIG, "displayName", "Corporate", "trustEmail", true,
                        "firstBrokerLoginFlowAlias", "first broker login"), response -> {
                            assertFalse(response.isError(), text(response));
                            assertFalse(text(response).contains(SECRET), text(response));
                            JsonNode provider = readTree(text(response));
                            assertEquals(alias, provider.get("alias").asText());
                            assertEquals("Corporate", provider.get("displayName").asText());
                            assertEquals("oidc", provider.get("providerId").asText());
                            assertTrue(provider.get("enabled").asBoolean());
                            assertTrue(provider.get("trustEmail").asBoolean());
                            assertEquals("first broker login", provider.get("firstBrokerLoginFlowAlias").asText());
                            assertFalse(provider.get("firstBrokerLoginFlowFromRealm").asBoolean());
                            assertEquals("**********", provider.get("config").get("clientSecret").asText());
                            assertEquals("https://idp.example/token", provider.get("config").get("tokenUrl").asText());
                        })
                .thenAssertResults();
        client.when()
                .toolsCall("getIdentityProvider", Map.of("realm", "acme", "alias", alias), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode provider = readTree(text(response));
                    assertTrue(provider.get("trustEmail").asBoolean());
                    for (String key : OIDC_CONFIG.keySet()) {
                        assertTrue(provider.get("config").has(key), key + " missing: " + text(response));
                    }
                    assertEquals("**********", provider.get("config").get("clientSecret").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createIdentityProviderRefusesAnUnknownType() {
        String alias = throwawayAlias();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createIdentityProvider", Map.of("realm", "acme", "alias", alias, "providerId", "nope",
                        "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Identity provider type nope is not a type of this server"),
                                    text(response));
                            assertTrue(text(response).contains("oidc"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertFalse(exists("acme", alias));
    }

    @Test
    void createIdentityProviderPassesKeycloaksConflictOnADuplicateAlias() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createIdentityProvider", Map.of("realm", "acme", "alias", alias, "providerId", "oidc",
                        "config", OIDC_CONFIG), response -> {
                            assertTrue(response.isError());
                            assertEquals("Keycloak returned 409 Conflict: Identity Provider " + alias + " already exists",
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void createIdentityProviderIsRefusedForMaster() {
        String alias = throwawayAlias();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createIdentityProvider", Map.of("realm", "master", "alias", alias, "providerId", "oidc",
                        "config", OIDC_CONFIG), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("createIdentityProvider is refused for the master realm"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertFalse(exists("master", alias));
    }

    IdentityProviderRepresentation stored(String alias) {
        return acme().identityProviders().get(alias).toRepresentation();
    }

    @Test
    void updateIdentityProviderChangesOnlyTheSuppliedFieldAndKey() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "displayName", "Renamed",
                        "config", Map.of("authorizationUrl", "https://idp.example/v2/auth")), response -> {
                            assertFalse(response.isError(), text(response));
                            assertFalse(text(response).contains(SECRET), text(response));
                            JsonNode provider = readTree(text(response));
                            assertEquals("Renamed", provider.get("displayName").asText());
                            assertTrue(provider.get("enabled").asBoolean());
                            assertTrue(provider.get("trustEmail").asBoolean());
                            JsonNode config = provider.get("config");
                            assertEquals("https://idp.example/v2/auth", config.get("authorizationUrl").asText());
                            assertEquals("https://idp.example/token", config.get("tokenUrl").asText());
                            assertEquals("acme-broker", config.get("clientId").asText());
                            assertEquals("client_secret_post", config.get("clientAuthMethod").asText());
                            assertEquals("**********", config.get("clientSecret").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        // Keycloak never returns the stored value; dropping the key on update would erase it, and the key would vanish
        assertTrue(stored(alias).getConfig().containsKey("clientSecret"), stored(alias).getConfig().toString());
    }

    @Test
    void updateIdentityProviderPassesKeycloaksRefusalToMoveTheSecretToANewTokenUrl() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "config",
                        Map.of("tokenUrl", "https://elsewhere.example/token")), response -> {
                            assertTrue(response.isError());
                            assertEquals("Keycloak returned 400 Bad Request: Client secret must be re-entered when the"
                                    + " token URL, client ID, authentication method, or related destination settings"
                                    + " are changed", text(response));
                        })
                .thenAssertResults();
        assertEquals("https://idp.example/token", stored(alias).getConfig().get("tokenUrl"));

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "config",
                        Map.of("tokenUrl", "https://elsewhere.example/token", "clientSecret", "n3w-secret")),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertFalse(text(response).contains("n3w-secret"), text(response));
                            JsonNode config = readTree(text(response)).get("config");
                            assertEquals("https://elsewhere.example/token", config.get("tokenUrl").asText());
                            assertEquals("**********", config.get("clientSecret").asText());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void updateIdentityProviderNeedsConfirmToDisableAnEnabledProvider() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "enabled", false,
                        "displayName", "Not applied"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Disabling identity provider " + alias),
                                    text(response));
                            assertTrue(text(response).contains("confirm=true"), text(response));
                        })
                .thenAssertResults();
        assertTrue(stored(alias).isEnabled());
        assertEquals("Throwaway", stored(alias).getDisplayName());

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "enabled", false,
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertFalse(readTree(text(response)).get("enabled").asBoolean());
                        })
                .thenAssertResults();
        assertFalse(stored(alias).isEnabled());

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "enabled", false,
                        "displayName", "Still disabled"), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Still disabled", readTree(text(response)).get("displayName").asText());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void deleteIdentityProviderNeedsConfirm() {
        String alias = createThrowawayProvider();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteIdentityProvider", Map.of("realm", "acme", "alias", alias, "confirm", false),
                        response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertTrue(exists("acme", alias));

        client.when()
                .toolsCall("deleteIdentityProvider", Map.of("realm", "acme", "alias", alias, "confirm", true),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Deleted identity provider " + alias + " in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertFalse(exists("acme", alias));
    }

    @Test
    void updateAndDeleteNameAnUnknownAlias() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", "nope", "displayName", "x"),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Identity provider nope not found in realm acme", text(response));
                        })
                .toolsCall("deleteIdentityProvider", Map.of("realm", "acme", "alias", "nope", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Identity provider nope not found in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void updateAndDeleteAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "master", "alias", "any", "displayName", "x"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("updateIdentityProvider is refused for the master realm"),
                                    text(response));
                        })
                .toolsCall("deleteIdentityProvider", Map.of("realm", "master", "alias", "any", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("deleteIdentityProvider is refused for the master realm"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getIdentityProviderNamesAnUnknownAlias() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getIdentityProvider", Map.of("realm", "acme", "alias", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Identity provider nope not found in realm acme", text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }
}
