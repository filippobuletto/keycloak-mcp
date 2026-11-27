package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.IdentityProviderToolsTest.withField;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionConfigRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RequiredActionToolsTest {

    /** The required actions of a fresh 26.8.2 realm, in priority order. */
    static final List<String> FRESH_ORDER = List.of("TERMS_AND_CONDITIONS", "UPDATE_PROFILE", "VERIFY_EMAIL",
            "CONFIGURE_TOTP", "UPDATE_PASSWORD", "delete_account", "UPDATE_EMAIL", "webauthn-register",
            "webauthn-register-passwordless", "VERIFY_PROFILE", "delete_credential", "idp_link",
            "CONFIGURE_RECOVERY_AUTHN_CODES", "update_user_locale");

    static final String PREFIX = "tmp-ra-";

    @Inject
    Keycloak keycloak;

    List<RequiredActionProviderRepresentation> snapshot;
    Map<String, Map<String, String>> configs;

    AuthenticationManagementResource flows() {
        return keycloak.realm("acme").flows();
    }

    @BeforeEach
    void takeSnapshot() {
        snapshot = flows().getRequiredActions();
        configs = new HashMap<>();
        for (RequiredActionProviderRepresentation action : snapshot) {
            config(action.getAlias()).ifPresent(config -> configs.put(action.getAlias(), config));
        }
    }

    /** The stored config, or empty when the action is not configurable. */
    java.util.Optional<Map<String, String>> config(String alias) {
        try {
            RequiredActionConfigRepresentation config = flows().getRequiredActionConfig(alias);
            return java.util.Optional.of(config.getConfig() == null ? Map.of() : Map.copyOf(config.getConfig()));
        } catch (WebApplicationException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Rebinds acme's flows and deletes the flow copies, then sends every action back as it was (full representation,
     * so flags and order return) and restores configs.
     */
    @AfterEach
    void restoreSnapshot() {
        AuthenticationFlowToolsTest.restoreBindings(keycloak.realm("acme"), PREFIX);
        flows().getFlows().stream()
                .filter(flow -> flow.getAlias().startsWith(PREFIX))
                .forEach(flow -> flows().deleteFlow(flow.getId()));
        for (RequiredActionProviderRepresentation action : snapshot) {
            flows().updateRequiredAction(action.getAlias(), action);
        }
        configs.forEach((alias, config) -> {
            if (!config.equals(config(alias).orElse(config))) {
                if (config.isEmpty()) {
                    flows().removeRequiredActionConfig(alias);
                } else {
                    RequiredActionConfigRepresentation rep = new RequiredActionConfigRepresentation();
                    rep.setConfig(new HashMap<>(config));
                    flows().updateRequiredActionConfig(alias, rep);
                }
            }
        });
    }

    static List<String> field(JsonNode array, String name) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> node.get(name).asText())
                .toList();
    }

    @Test
    void listRequiredActionsShowsTheActionsInOrderWithTheirFlags() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listRequiredActions", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode actions = readTree(text(response));
                    assertEquals(FRESH_ORDER, field(actions, "alias"));
                    JsonNode totp = withField(actions, "alias", "CONFIGURE_TOTP");
                    assertEquals("Configure OTP", totp.get("name").asText());
                    assertTrue(totp.get("enabled").asBoolean());
                    assertFalse(totp.get("defaultAction").asBoolean());
                    assertFalse(withField(actions, "alias", "TERMS_AND_CONDITIONS").get("enabled").asBoolean());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getRequiredActionShowsPlaceConfigAndConfigProperties() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode action = readTree(text(response));
                    assertEquals("CONFIGURE_TOTP", action.get("alias").asText());
                    assertEquals("CONFIGURE_TOTP", action.get("providerId").asText());
                    assertEquals(FRESH_ORDER.indexOf("CONFIGURE_TOTP") + 1, action.get("place").asInt());
                    assertTrue(action.get("config").isEmpty(), text(response));
                    assertEquals(List.of("max_auth_age", "add-recovery-codes"),
                            field(action.get("configProperties"), "key"));
                })
                .toolsCall("getRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_RECOVERY_AUTHN_CODES"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertTrue(field(readTree(text(response)).get("configProperties"), "key")
                                    .containsAll(List.of("warning_threshold", "max_auth_age")), text(response));
                        })
                .toolsCall("getRequiredAction", Map.of("realm", "acme", "alias", "delete_account"), response -> {
                    assertFalse(response.isError(), text(response));
                    assertTrue(readTree(text(response)).get("configProperties").isEmpty(), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void anUnknownAliasOrRealmFails() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getRequiredAction", Map.of("realm", "acme", "alias", "NO_SUCH_ACTION"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("Required action NO_SUCH_ACTION not found in realm acme"),
                            text(response));
                    assertTrue(text(response).contains("listRequiredActions"), text(response));
                })
                .toolsCall("listRequiredActions", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    RequiredActionProviderRepresentation stored(String alias) {
        return flows().getRequiredAction(alias);
    }

    /** Copies browser, adds a top-level REQUIRED OTP step and binds the copy as acme's browser flow. */
    String bindBrowserRequiringOtp() {
        String alias = PREFIX + UUID.randomUUID();
        flows().copy("browser", Map.of("newName", alias)).close();
        flows().addExecution(alias, Map.of("provider", "auth-otp-form"));
        AuthenticationExecutionInfoRepresentation otp = flows().getExecutions(alias).stream()
                .filter(e -> e.getLevel() == 0 && "auth-otp-form".equals(e.getProviderId()))
                .findFirst()
                .orElseThrow();
        otp.setRequirement("REQUIRED");
        flows().updateExecutions(alias, otp);
        RealmRepresentation binding = new RealmRepresentation();
        binding.setBrowserFlow(alias);
        keycloak.realm("acme").update(binding);
        return alias;
    }

    @Test
    void updateRequiredActionChangesOnlyTheGivenField() {
        RequiredActionProviderRepresentation before = stored("CONFIGURE_TOTP");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP", "defaultAction",
                        true, "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode action = readTree(text(response));
                            assertTrue(action.get("defaultAction").asBoolean(), text(response));
                            assertTrue(action.get("enabled").asBoolean(), text(response));
                            assertEquals(FRESH_ORDER.indexOf("CONFIGURE_TOTP") + 1, action.get("place").asInt());
                        })
                .thenAssertResults();

        client.disconnect();
        RequiredActionProviderRepresentation after = stored("CONFIGURE_TOTP");
        assertEquals(before.getAlias(), after.getAlias());
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getPriority(), after.getPriority());
        assertEquals(before.getConfig(), after.getConfig());
        assertTrue(after.isEnabled());
        assertTrue(after.isDefaultAction());
    }

    @Test
    void turningOnADefaultActionNeedsConfirm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "VERIFY_EMAIL", "defaultAction",
                        true), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("every new user"), text(response));
                            assertTrue(text(response).contains("Call again with confirm=true"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertFalse(stored("VERIFY_EMAIL").isDefaultAction());
    }

    @Test
    void changesWithoutRiskNeedNoConfirm() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "TERMS_AND_CONDITIONS", "enabled",
                        false), response -> assertFalse(response.isError(), text(response)))
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "UPDATE_EMAIL", "enabled", true),
                        response -> assertFalse(response.isError(), text(response)))
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP", "defaultAction",
                        false), response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored("UPDATE_EMAIL").isEnabled());
        assertFalse(stored("TERMS_AND_CONDITIONS").isEnabled());
    }

    @Test
    void disablingOtpSetupNamesNoFlowWhileAcmeUsesTheBuiltInBrowserFlow() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP", "enabled",
                        false), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("CONFIGURE_TOTP"), text(response));
                            assertFalse(text(response).contains("cannot sign in"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored("CONFIGURE_TOTP").isEnabled());
    }

    @Test
    void disablingOtpSetupNamesABoundFlowThatRequiresOtp() {
        String alias = bindBrowserRequiringOtp();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP", "enabled",
                        false), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains(alias + " (realm: BROWSER)"), text(response));
                            assertTrue(text(response).contains("cannot sign in"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(stored("CONFIGURE_TOTP").isEnabled());
    }

    @Test
    void updateRequiredActionWithoutAFieldOrOnMasterFails() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "CONFIGURE_TOTP"), response -> {
                    assertTrue(response.isError(), text(response));
                    assertTrue(text(response).contains("Give enabled or defaultAction"), text(response));
                })
                .toolsCall("updateRequiredAction", Map.of("realm", "master", "alias", "CONFIGURE_TOTP", "enabled",
                        true), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    List<String> storedOrder() {
        return flows().getRequiredActions().stream().map(RequiredActionProviderRepresentation::getAlias).toList();
    }

    @Test
    void moveRequiredActionUpSwapsWithThePreviousAction() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("moveRequiredAction", Map.of("realm", "acme", "alias", "VERIFY_EMAIL", "direction", "UP"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            List<String> order = field(readTree(text(response)), "alias");
                            assertEquals(List.of("TERMS_AND_CONDITIONS", "VERIFY_EMAIL", "UPDATE_PROFILE"),
                                    order.subList(0, 3));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(List.of("TERMS_AND_CONDITIONS", "VERIFY_EMAIL", "UPDATE_PROFILE"), storedOrder().subList(0, 3));
    }

    @Test
    void moveRequiredActionPastEitherEndFails() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("moveRequiredAction", Map.of("realm", "acme", "alias", "TERMS_AND_CONDITIONS", "direction",
                        "UP"), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("TERMS_AND_CONDITIONS is already the first"),
                                    text(response));
                        })
                .toolsCall("moveRequiredAction", Map.of("realm", "acme", "alias", "update_user_locale", "direction",
                        "DOWN"), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("update_user_locale is already the last"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(FRESH_ORDER, storedOrder());
    }

    @Test
    void setRequiredActionConfigStoresAKey() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setRequiredActionConfig", Map.of("realm", "acme", "alias", "UPDATE_PASSWORD", "config",
                        Map.of("max_auth_age", "600")), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("600", readTree(text(response)).get("config").get("max_auth_age").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of("max_auth_age", "600"), config("UPDATE_PASSWORD").orElseThrow());
    }

    @Test
    void setRequiredActionConfigMergesIntoTheStoredConfig() {
        McpStreamableTestClient client = McpClients.connected();
        Map<String, Object> threshold = Map.of("realm", "acme", "alias", "CONFIGURE_RECOVERY_AUTHN_CODES", "config",
                Map.of("warning_threshold", "2"));
        Map<String, Object> age = Map.of("realm", "acme", "alias", "CONFIGURE_RECOVERY_AUTHN_CODES", "config",
                Map.of("max_auth_age", "300"));

        client.when()
                .toolsCall("setRequiredActionConfig", threshold, response -> assertFalse(response.isError(),
                        text(response)))
                .thenAssertResults();
        client.when()
                .toolsCall("setRequiredActionConfig", age, response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of("warning_threshold", "2", "max_auth_age", "300"),
                config("CONFIGURE_RECOVERY_AUTHN_CODES").orElseThrow());
    }

    @Test
    void setRequiredActionConfigRefusesAnUndeclaredKeyAndPassesOnABadValue() {
        RequiredActionConfigRepresentation stored = new RequiredActionConfigRepresentation();
        stored.setConfig(new HashMap<>(Map.of("max_auth_age", "600")));
        flows().updateRequiredActionConfig("UPDATE_PASSWORD", stored);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setRequiredActionConfig", Map.of("realm", "acme", "alias", "UPDATE_PASSWORD", "config",
                        Map.of("no_such_key", "x")), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains(
                                    "Config key no_such_key is not declared by required action UPDATE_PASSWORD"),
                                    text(response));
                        })
                .toolsCall("setRequiredActionConfig", Map.of("realm", "acme", "alias", "UPDATE_PASSWORD", "config",
                        Map.of("max_auth_age", "abc")), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("400"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of("max_auth_age", "600"), config("UPDATE_PASSWORD").orElseThrow());
    }

    @Test
    void deleteRequiredActionConfigEmptiesTheConfig() {
        RequiredActionConfigRepresentation stored = new RequiredActionConfigRepresentation();
        stored.setConfig(new HashMap<>(Map.of("max_auth_age", "600")));
        flows().updateRequiredActionConfig("UPDATE_PASSWORD", stored);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteRequiredActionConfig", Map.of("realm", "acme", "alias", "UPDATE_PASSWORD"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertTrue(readTree(text(response)).get("config").isEmpty(), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of(), config("UPDATE_PASSWORD").orElseThrow());
    }

    @Test
    void theConfigToolsRefuseAnActionThatTakesNoConfig() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setRequiredActionConfig", Map.of("realm", "acme", "alias", "delete_account", "config",
                        Map.of("max_auth_age", "600")), response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("Required action delete_account takes no config"),
                                    text(response));
                        })
                .toolsCall("deleteRequiredActionConfig", Map.of("realm", "acme", "alias", "delete_account"),
                        response -> {
                            assertTrue(response.isError(), text(response));
                            assertTrue(text(response).contains("Required action delete_account takes no config"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void moveAndConfigToolsAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("moveRequiredAction", Map.of("realm", "master", "alias", "VERIFY_EMAIL", "direction", "UP"),
                        response -> assertTrue(text(response).contains("refused for the master realm"),
                                text(response)))
                .toolsCall("setRequiredActionConfig", Map.of("realm", "master", "alias", "UPDATE_PASSWORD", "config",
                        Map.of("max_auth_age", "600")),
                        response -> assertTrue(text(response).contains("refused for the master realm"),
                                text(response)))
                .toolsCall("deleteRequiredActionConfig", Map.of("realm", "master", "alias", "UPDATE_PASSWORD"),
                        response -> assertTrue(text(response).contains("refused for the master realm"),
                                text(response)))
                .thenAssertResults();

        client.disconnect();
    }
}
