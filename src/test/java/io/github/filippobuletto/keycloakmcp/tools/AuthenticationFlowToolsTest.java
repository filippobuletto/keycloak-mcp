package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.IdentityProviderToolsTest.withField;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.readTree;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AuthenticationFlowToolsTest {

    static final String PREFIX = "tmp-flow-";

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    /** Copies acme's built-in browser flow under a tmp-flow-... alias; {@link #deleteThrowaways} removes it. */
    String copyBrowser() {
        String alias = PREFIX + UUID.randomUUID();
        acme().flows().copy("browser", Map.of("newName", alias)).close();
        return alias;
    }

    String flowId(String alias) {
        return acme().flows().getFlows().stream()
                .filter(flow -> flow.getAlias().equals(alias))
                .map(AuthenticationFlowRepresentation::getId)
                .findFirst()
                .orElseThrow();
    }

    /** Creates a client whose browser flow override is {@code flowAlias}. */
    String createClientOverriding(String flowAlias) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(PREFIX + UUID.randomUUID());
        client.setAuthenticationFlowBindingOverrides(Map.of("browser", flowId(flowAlias)));
        acme().clients().create(client).close();
        return client.getClientId();
    }

    /** Creates an oidc identity provider whose first broker login flow is {@code flowAlias}. */
    String createProviderUsing(String flowAlias) {
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias(PREFIX + UUID.randomUUID());
        provider.setProviderId("oidc");
        provider.setFirstBrokerLoginFlowAlias(flowAlias);
        provider.setConfig(new HashMap<>(IdentityProviderToolsTest.OIDC_CONFIG));
        acme().identityProviders().create(provider).close();
        return provider.getAlias();
    }

    /** Binds acme's built-in flows again when a test left a slot on a flow whose alias starts with {@code prefix}. */
    static void restoreBindings(RealmResource realm, String prefix) {
        RealmRepresentation current = realm.toRepresentation();
        if (java.util.stream.Stream.of(current.getBrowserFlow(), current.getRegistrationFlow(),
                current.getDirectGrantFlow(), current.getResetCredentialsFlow(), current.getClientAuthenticationFlow(),
                current.getDockerAuthenticationFlow(), current.getFirstBrokerLoginFlow())
                .anyMatch(alias -> alias != null && alias.startsWith(prefix))) {
            RealmRepresentation defaults = new RealmRepresentation();
            defaults.setBrowserFlow("browser");
            defaults.setRegistrationFlow("registration");
            defaults.setDirectGrantFlow("direct grant");
            defaults.setResetCredentialsFlow("reset credentials");
            defaults.setClientAuthenticationFlow("clients");
            defaults.setDockerAuthenticationFlow("docker auth");
            defaults.setFirstBrokerLoginFlow("first broker login");
            realm.update(defaults);
        }
    }

    /** Users go first: Keycloak refuses to delete a flow in use. */
    @AfterEach
    void deleteThrowaways() {
        restoreBindings(acme(), PREFIX);
        acme().identityProviders().findAll().stream()
                .filter(provider -> provider.getAlias().startsWith(PREFIX))
                .forEach(provider -> acme().identityProviders().get(provider.getAlias()).remove());
        acme().clients().findAll().stream()
                .filter(client -> client.getClientId().startsWith(PREFIX))
                .forEach(client -> acme().clients().get(client.getId()).remove());
        acme().flows().getFlows().stream()
                .filter(flow -> flow.getAlias().startsWith(PREFIX))
                .forEach(flow -> acme().flows().deleteFlow(flow.getId()));
    }

    static List<String> strings(JsonNode array) {
        return array == null ? List.of()
                : java.util.stream.StreamSupport.stream(array.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList();
    }

    @Test
    void listAuthenticationFlowsShowsTheBuiltInFlowsWithTheirRealmSlots() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listAuthenticationFlows", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode flows = readTree(text(response));
                    Map<String, String> slots = Map.of("browser", "realm: BROWSER", "direct grant",
                            "realm: DIRECT_GRANT", "registration", "realm: REGISTRATION", "reset credentials",
                            "realm: RESET_CREDENTIALS", "clients", "realm: CLIENT_AUTHENTICATION",
                            "first broker login", "realm: FIRST_BROKER_LOGIN", "docker auth",
                            "realm: DOCKER_AUTHENTICATION");
                    slots.forEach((alias, slot) -> {
                        JsonNode flow = withField(flows, "alias", alias);
                        assertNotNull(flow, alias + " in " + text(response));
                        assertTrue(flow.get("builtIn").asBoolean(), alias);
                        assertTrue(strings(flow.get("usedBy")).contains(slot), alias + " " + flow);
                    });
                    assertEquals("client-flow", withField(flows, "alias", "clients").get("type").asText());
                    assertEquals("basic-flow", withField(flows, "alias", "browser").get("type").asText());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listAuthenticationFlowsNamesClientsAndProvidersThatUseAFlow() {
        String alias = copyBrowser();
        String clientId = createClientOverriding(alias);
        String provider = createProviderUsing(alias);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listAuthenticationFlows", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode flow = withField(readTree(text(response)), "alias", alias);
                    assertNotNull(flow, text(response));
                    assertFalse(flow.get("builtIn").asBoolean());
                    assertEquals(List.of("client " + clientId + ": BROWSER",
                            "identity provider " + provider + ": FIRST_BROKER_LOGIN"), strings(flow.get("usedBy")));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getAuthenticationFlowShowsTheExecutionsInOrder() {
        String alias = copyBrowser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getAuthenticationFlow", Map.of("realm", "acme", "alias", alias), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode flow = readTree(text(response));
                    assertEquals(alias, flow.get("alias").asText());
                    assertEquals("basic-flow", flow.get("type").asText());
                    assertFalse(flow.get("builtIn").asBoolean());
                    assertTrue(flow.get("usedBy").isEmpty(), text(response));
                    JsonNode executions = flow.get("executions");

                    JsonNode cookie = executions.get(0);
                    assertEquals("Cookie", cookie.get("displayName").asText());
                    assertEquals("auth-cookie", cookie.get("providerId").asText());
                    assertEquals(0, cookie.get("level").asInt());
                    assertEquals("ALTERNATIVE", cookie.get("requirement").asText());
                    assertEquals(List.of("REQUIRED", "ALTERNATIVE", "DISABLED"),
                            strings(cookie.get("requirementChoices")));
                    assertFalse(cookie.get("id").asText().isBlank());

                    JsonNode forms = withField(executions, "displayName", alias + " forms");
                    assertNotNull(forms, text(response));
                    assertEquals(alias + " forms", forms.get("subFlowAlias").asText());
                    assertTrue(forms.get("providerId").isNull(), forms.toString());

                    JsonNode password = withField(executions, "providerId", "auth-username-password-form");
                    assertEquals(1, password.get("level").asInt());

                    JsonNode condition = withField(executions, "providerId", "conditional-credential");
                    assertTrue(condition.get("configurable").asBoolean());
                    assertFalse(condition.get("configAlias").asText().isBlank(), condition.toString());
                    assertTrue(condition.get("config").isObject(), condition.toString());
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getAuthenticationFlowNamesAnUnknownAlias() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("getAuthenticationFlow", Map.of("realm", "acme", "alias", "nope"), response -> {
                    assertTrue(response.isError());
                    assertEquals("Authentication flow nope not found in realm acme", text(response));
                })
                .toolsCall("listAuthenticationFlows", Map.of("realm", "no-such-realm"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).startsWith("Keycloak returned 404"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listAuthenticatorTypesShowsEveryKind() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listAuthenticatorTypes", Map.of("realm", "acme"), response -> {
                    assertFalse(response.isError(), text(response));
                    JsonNode types = readTree(text(response));
                    Map<String, String> kinds = Map.of("auth-otp-form", "authenticator", "registration-page-form",
                            "form", "registration-user-creation", "form-action", "client-secret",
                            "client-authenticator");
                    kinds.forEach((id, kind) -> {
                        JsonNode type = withField(types, "id", id);
                        assertNotNull(type, id + " in " + text(response));
                        assertEquals(kind, type.get("kind").asText(), id);
                        assertFalse(type.get("name").asText().isBlank(), id);
                    });
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void listAuthenticatorTypesShowsTheConfigPropertiesOfOneType() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("listAuthenticatorTypes",
                        Map.of("realm", "acme", "providerId", "identity-provider-redirector"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode types = readTree(text(response));
                            assertEquals(1, types.size(), text(response));
                            JsonNode property = withField(types.get(0).get("properties"), "key", "defaultProvider");
                            assertNotNull(property, text(response));
                            assertFalse(property.get("label").asText().isBlank());
                        })
                .toolsCall("listAuthenticatorTypes", Map.of("realm", "acme", "providerId", "nope"), response -> {
                    assertTrue(response.isError());
                    assertTrue(text(response).startsWith("Authenticator type nope not found"), text(response));
                })
                .thenAssertResults();

        client.disconnect();
    }

    boolean flowExists(String alias) {
        return acme().flows().getFlows().stream().anyMatch(flow -> flow.getAlias().equals(alias));
    }

    @Test
    void createAuthenticationFlowCreatesAnEmptyFlowOfEitherType() {
        String basic = PREFIX + UUID.randomUUID();
        String clientFlow = PREFIX + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createAuthenticationFlow", Map.of("realm", "acme", "alias", basic, "description",
                        "Kiosk sign-in", "type", "BASIC"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode flow = readTree(text(response));
                            assertEquals(basic, flow.get("alias").asText());
                            assertEquals("Kiosk sign-in", flow.get("description").asText());
                            assertEquals("basic-flow", flow.get("type").asText());
                            assertFalse(flow.get("builtIn").asBoolean());
                            assertTrue(flow.get("executions").isEmpty(), text(response));
                        })
                .toolsCall("createAuthenticationFlow", Map.of("realm", "acme", "alias", clientFlow, "type", "CLIENT"),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("client-flow", readTree(text(response)).get("type").asText());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("createAuthenticationFlow", Map.of("realm", "acme", "alias", basic, "type", "BASIC"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("already exists"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void copyAuthenticationFlowCopiesTheExecutions() {
        String alias = PREFIX + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("copyAuthenticationFlow", Map.of("realm", "acme", "alias", "browser", "newAlias", alias),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode flow = readTree(text(response));
                            assertEquals(alias, flow.get("alias").asText());
                            assertFalse(flow.get("builtIn").asBoolean());
                            assertEquals(acme().flows().getExecutions("browser").stream()
                                    .map(e -> e.getLevel() + " " + e.getRequirement() + " " + e.getProviderId())
                                    .toList(), steps(flow.get("executions")));
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("copyAuthenticationFlow", Map.of("realm", "acme", "alias", "browser", "newAlias", alias),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("New flow alias name already exists"), text(response));
                        })
                .toolsCall("copyAuthenticationFlow", Map.of("realm", "acme", "alias", "nope", "newAlias",
                        PREFIX + UUID.randomUUID()), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow nope not found in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    /** Level, requirement and provider id per step, as text; a sub-flow's provider id reads null. */
    static List<String> steps(JsonNode executions) {
        return java.util.stream.StreamSupport.stream(executions.spliterator(), false)
                .map(e -> e.get("level").asInt() + " " + e.get("requirement").asText() + " "
                        + (e.get("providerId").isNull() ? null : e.get("providerId").asText()))
                .toList();
    }

    @Test
    void deleteAuthenticationFlowNeedsConfirmAndRemovesAnUnusedFlow() {
        String alias = copyBrowser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", alias, "confirm", false),
                        response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertTrue(flowExists(alias));

        client.when()
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", alias, "confirm", true),
                        response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Deleted authentication flow " + alias + " in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertFalse(flowExists(alias));
    }

    @Test
    void deleteAuthenticationFlowRefusesAFlowInUseAndNamesItsUsers() {
        String alias = copyBrowser();
        String clientId = createClientOverriding(alias);
        String provider = createProviderUsing(alias);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", alias, "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow " + alias + " is in use by client " + clientId
                                    + ": BROWSER, identity provider " + provider + ": FIRST_BROKER_LOGIN."
                                    + " Bind another flow there first. Nothing was changed.", text(response));
                        })
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", "browser", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow browser is built in and cannot be deleted."
                                    + " Nothing was changed.", text(response));
                        })
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", "nope", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow nope not found in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(flowExists(alias));
        assertTrue(flowExists("browser"));
    }

    @Test
    void flowWritesAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("createAuthenticationFlow", Map.of("realm", "master", "alias", "x", "type", "BASIC"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "createAuthenticationFlow is refused for the master realm"), text(response));
                        })
                .toolsCall("copyAuthenticationFlow", Map.of("realm", "master", "alias", "browser", "newAlias", "x"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "copyAuthenticationFlow is refused for the master realm"), text(response));
                        })
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "master", "alias", "x", "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith(
                                    "deleteAuthenticationFlow is refused for the master realm"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    /** The executions of a flow as Keycloak stores them. */
    List<org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation> executions(String alias) {
        return acme().flows().getExecutions(alias);
    }

    String executionId(String alias, String displayName) {
        return executions(alias).stream()
                .filter(e -> e.getDisplayName().equals(displayName))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** Adds auth-otp-form at the end of the copy through the admin client and returns its execution id. */
    String addOtp(String alias) {
        acme().flows().addExecution(alias, Map.of("provider", "auth-otp-form"));
        List<org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation> all = executions(alias);
        return all.get(all.size() - 1).getId();
    }

    static JsonNode last(JsonNode executions) {
        return executions.get(executions.size() - 1);
    }

    @Test
    void addFlowExecutionAddsADisabledStepAtTheEndOfTheFlowOrASubFlow() {
        String alias = copyBrowser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "providerId",
                        "auth-otp-form"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode step = last(readTree(text(response)).get("executions"));
                            assertEquals("auth-otp-form", step.get("providerId").asText());
                            assertEquals(0, step.get("level").asInt());
                            assertEquals("DISABLED", step.get("requirement").asText());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("addFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "parent", alias + " forms",
                        "providerId", "auth-otp-form"), response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();
        assertEquals(1, executions(alias).stream()
                .filter(e -> "auth-otp-form".equals(e.getProviderId()) && e.getLevel() == 1).count());

        client.when()
                .toolsCall("addFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "providerId", "nope"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("No authentication provider found for id: nope"),
                                    text(response));
                        })
                .toolsCall("addFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "parent", "nope",
                        "providerId", "auth-otp-form"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Sub-flow nope not found in authentication flow " + alias
                                    + " of realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void addFlowSubFlowAddsABasicOrFormSubFlow() {
        String alias = copyBrowser();
        String basic = PREFIX + UUID.randomUUID();
        String form = PREFIX + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", alias, "alias", basic, "type",
                        "BASIC"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode step = last(readTree(text(response)).get("executions"));
                            assertEquals(basic, step.get("subFlowAlias").asText());
                            assertEquals(0, step.get("level").asInt());
                            assertEquals("DISABLED", step.get("requirement").asText());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", alias, "parent", basic, "alias",
                        form, "type", "FORM", "formProviderId", "registration-page-form"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode step = last(readTree(text(response)).get("executions"));
                            assertEquals(form, step.get("subFlowAlias").asText());
                            assertEquals(1, step.get("level").asInt());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", alias, "alias",
                        PREFIX + UUID.randomUUID(), "type", "FORM"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("A FORM sub-flow needs formProviderId"),
                                    text(response));
                        })
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", alias, "alias",
                        PREFIX + UUID.randomUUID(), "type", "FORM", "formProviderId", "auth-otp-form"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("auth-otp-form is not a form type"), text(response));
                        })
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", alias, "alias", basic, "type",
                        "BASIC"), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("New flow alias name already exists"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void updateFlowExecutionSetsOnlyAnAllowedRequirement() {
        String alias = copyBrowser();
        String otp = addOtp(alias);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("updateFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", otp,
                        "requirement", "REQUIRED"), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("REQUIRED",
                                    withField(readTree(text(response)).get("executions"), "id", otp)
                                            .get("requirement").asText());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("updateFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", otp,
                        "requirement", "CONDITIONAL"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution OTP Form allows only REQUIRED, ALTERNATIVE, DISABLED;"
                                    + " CONDITIONAL is refused. Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals("REQUIRED", executions(alias).stream().filter(e -> e.getId().equals(otp)).findFirst()
                .orElseThrow().getRequirement());
    }

    @Test
    void moveFlowExecutionSwapsWithASiblingAndRefusesAtTheEdges() {
        String alias = copyBrowser();
        String kerberos = executionId(alias, "Kerberos");
        String cookie = executionId(alias, "Cookie");
        String password = executionId(alias, "Username Password Form");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("moveFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", kerberos,
                        "direction", "UP"), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode executions = readTree(text(response)).get("executions");
                            assertEquals("Kerberos", executions.get(0).get("displayName").asText());
                            assertEquals("Cookie", executions.get(1).get("displayName").asText());
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("moveFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", kerberos,
                        "direction", "UP"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution Kerberos is already the first of its level. Nothing was changed.",
                                    text(response));
                        })
                .toolsCall("moveFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", password,
                        "direction", "UP"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution Username Password Form is already the first of its level."
                                    + " Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(kerberos, executions(alias).get(0).getId());
        assertEquals(cookie, executions(alias).get(1).getId());
    }

    @Test
    void moveFlowExecutionDownStopsAtTheLastSibling() {
        String alias = copyBrowser();
        String otp = addOtp(alias);
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("moveFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", otp,
                        "direction", "DOWN"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution OTP Form is already the last of its level. Nothing was changed.",
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void deleteFlowExecutionNeedsConfirmAndRemovesASubFlowWithItsSteps() {
        String alias = copyBrowser();
        String forms = executionId(alias, alias + " forms");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", forms,
                        "confirm", false), response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertTrue(executions(alias).stream().anyMatch(e -> e.getId().equals(forms)));

        client.when()
                .toolsCall("deleteFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId", forms,
                        "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Deleted execution " + alias + " forms from authentication flow " + alias
                                    + " in realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(executions(alias).stream().noneMatch(e -> e.getId().equals(forms)
                || "auth-username-password-form".equals(e.getProviderId())), executions(alias).toString());
    }

    @Test
    void executionToolsRefuseABuiltInFlowAndAnExecutionOfAnotherFlow() {
        String cookie = executionId("browser", "Cookie");
        String other = copyBrowser();
        String alias = copyBrowser();
        String otherCookie = executionId(other, "Cookie");
        String copyFirst = "Authentication flow browser is built in; copy it with copyAuthenticationFlow, change the"
                + " copy, then bind the copy with bindRealmFlow or setClientFlowOverride. Nothing was changed.";
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("addFlowExecution", Map.of("realm", "acme", "flowAlias", "browser", "providerId",
                        "auth-otp-form"), response -> assertEquals(copyFirst, text(response)))
                .toolsCall("addFlowSubFlow", Map.of("realm", "acme", "flowAlias", "browser", "alias",
                        PREFIX + UUID.randomUUID(), "type", "BASIC"), response -> assertEquals(copyFirst, text(response)))
                .toolsCall("updateFlowExecution", Map.of("realm", "acme", "flowAlias", "browser", "executionId",
                        cookie, "requirement", "DISABLED"), response -> assertEquals(copyFirst, text(response)))
                .toolsCall("moveFlowExecution", Map.of("realm", "acme", "flowAlias", "browser", "executionId", cookie,
                        "direction", "DOWN"), response -> assertEquals(copyFirst, text(response)))
                .toolsCall("deleteFlowExecution", Map.of("realm", "acme", "flowAlias", "browser", "executionId",
                        cookie, "confirm", true), response -> assertEquals(copyFirst, text(response)))
                .toolsCall("updateFlowExecution", Map.of("realm", "acme", "flowAlias", alias, "executionId",
                        otherCookie, "requirement", "DISABLED"), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution " + otherCookie + " not found in authentication flow " + alias
                                    + " of realm acme", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        var browser = executions("browser");
        assertEquals(cookie, browser.get(0).getId());
        assertEquals("ALTERNATIVE", browser.get(0).getRequirement());
        assertTrue(browser.stream().noneMatch(e -> "auth-otp-form".equals(e.getProviderId()) && e.getLevel() == 0));
        assertEquals("ALTERNATIVE", executions(other).get(0).getRequirement());
    }

    @Test
    void executionWritesAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();
        Map<String, Map<String, Object>> calls = Map.of(
                "addFlowExecution", Map.of("realm", "master", "flowAlias", "x", "providerId", "auth-otp-form"),
                "addFlowSubFlow", Map.of("realm", "master", "flowAlias", "x", "alias", "y", "type", "BASIC"),
                "updateFlowExecution", Map.of("realm", "master", "flowAlias", "x", "executionId", "e", "requirement",
                        "REQUIRED"),
                "moveFlowExecution", Map.of("realm", "master", "flowAlias", "x", "executionId", "e", "direction", "UP"),
                "deleteFlowExecution", Map.of("realm", "master", "flowAlias", "x", "executionId", "e", "confirm",
                        true));

        var when = client.when();
        calls.forEach((tool, args) -> when.toolsCall(tool, args, response -> {
            assertTrue(response.isError());
            assertTrue(text(response).startsWith(tool + " is refused for the master realm"), text(response));
        }));
        when.thenAssertResults();

        client.disconnect();
    }

    org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation execution(String alias, String id) {
        return executions(alias).stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void setExecutionConfigCreatesThenMergesTheConfig() {
        String alias = copyBrowser();
        String redirector = executionId(alias, "Identity Provider Redirector");
        String configAlias = PREFIX + UUID.randomUUID();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", redirector,
                        "alias", configAlias, "config", Map.of("defaultProvider", "corporate-oidc")), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode step = withField(readTree(text(response)).get("executions"), "id", redirector);
                            assertEquals(configAlias, step.get("configAlias").asText());
                            assertEquals("corporate-oidc", step.get("config").get("defaultProvider").asText());
                        })
                .thenAssertResults();
        String configId = execution(alias, redirector).getAuthenticationConfig();

        client.when()
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", redirector,
                        "config", Map.of("defaultProvider", "partner-saml")), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode step = withField(readTree(text(response)).get("executions"), "id", redirector);
                            assertEquals(configAlias, step.get("configAlias").asText());
                            assertEquals("partner-saml", step.get("config").get("defaultProvider").asText());
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(configId, execution(alias, redirector).getAuthenticationConfig());
    }

    @Test
    void setExecutionConfigMergeKeepsTheOtherKeys() {
        String alias = copyBrowser();
        String condition = executionId(alias, "Condition - credential");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", condition,
                        "config", Map.of("included", "true")), response -> {
                            assertFalse(response.isError(), text(response));
                            JsonNode config = withField(readTree(text(response)).get("executions"), "id", condition)
                                    .get("config");
                            assertEquals("true", config.get("included").asText());
                            assertFalse(config.get("credentials").asText().isBlank(), config.toString());
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void setExecutionConfigRefusesBadInput() {
        String alias = copyBrowser();
        String redirector = executionId(alias, "Identity Provider Redirector");
        String cookie = executionId(alias, "Cookie");
        String condition = executionId(alias, "Condition - credential");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", redirector,
                        "alias", PREFIX + UUID.randomUUID(), "config", Map.of("defaultProvidr", "x")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Config key defaultProvidr is not declared by"
                                    + " authenticator type identity-provider-redirector; valid keys:"), text(response));
                        })
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", redirector,
                        "config", Map.of("defaultProvider", "x")), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution Identity Provider Redirector has no config yet; give an alias to"
                                    + " create one. Nothing was changed.", text(response));
                        })
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", cookie,
                        "alias", PREFIX + UUID.randomUUID(), "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution Cookie takes no config. Nothing was changed.", text(response));
                        })
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", condition,
                        "alias", "other", "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Execution Condition - credential already has config "),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(null, execution(alias, redirector).getAuthenticationConfig());
        assertEquals(null, execution(alias, cookie).getAuthenticationConfig());
    }

    @Test
    void deleteExecutionConfigNeedsConfirmAndRemovesTheConfig() {
        String alias = copyBrowser();
        String condition = executionId(alias, "Condition - credential");
        String cookie = executionId(alias, "Cookie");
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("deleteExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId",
                        condition, "confirm", false), response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertNotNull(execution(alias, condition).getAuthenticationConfig());

        client.when()
                .toolsCall("deleteExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId",
                        condition, "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Deleted the config of Condition - credential in authentication flow " + alias
                                    + " in realm acme", text(response));
                        })
                .thenAssertResults();
        assertEquals(null, execution(alias, condition).getAuthenticationConfig());

        client.when()
                .toolsCall("deleteExecutionConfig", Map.of("realm", "acme", "flowAlias", alias, "executionId", cookie,
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Execution Cookie has no config. Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void configToolsRefuseABuiltInFlowAndMaster() {
        String condition = executionId("browser", "Condition - credential");
        String before = acme().flows().getAuthenticatorConfig(execution("browser", condition).getAuthenticationConfig())
                .getConfig().toString();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setExecutionConfig", Map.of("realm", "acme", "flowAlias", "browser", "executionId",
                        condition, "config", Map.of("included", "true")), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Authentication flow browser is built in;"),
                                    text(response));
                        })
                .toolsCall("deleteExecutionConfig", Map.of("realm", "acme", "flowAlias", "browser", "executionId",
                        condition, "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("Authentication flow browser is built in;"),
                                    text(response));
                        })
                .toolsCall("setExecutionConfig", Map.of("realm", "master", "flowAlias", "x", "executionId", "e",
                        "config", Map.of()), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("setExecutionConfig is refused for the master realm"),
                                    text(response));
                        })
                .toolsCall("deleteExecutionConfig", Map.of("realm", "master", "flowAlias", "x", "executionId", "e",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("deleteExecutionConfig is refused for the master realm"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(before, acme().flows()
                .getAuthenticatorConfig(execution("browser", condition).getAuthenticationConfig()).getConfig()
                .toString());
    }

    String createClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(PREFIX + UUID.randomUUID());
        acme().clients().create(client).close();
        return client.getClientId();
    }

    Map<String, String> overrides(String clientId) {
        Map<String, String> overrides = acme().clients().findByClientId(clientId).getFirst()
                .getAuthenticationFlowBindingOverrides();
        return overrides == null ? Map.of() : overrides;
    }

    @Test
    void bindRealmFlowBindsACopyThatThenCannotBeDeleted() {
        String alias = copyBrowser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "BROWSER", "flowAlias", alias, "confirm",
                        false), response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertEquals("browser", acme().toRepresentation().getBrowserFlow());

        client.when()
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "BROWSER", "flowAlias", alias, "confirm",
                        true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Bound authentication flow " + alias
                                    + " to BROWSER in realm acme; it replaces browser", text(response));
                        })
                .thenAssertResults();
        assertEquals(alias, acme().toRepresentation().getBrowserFlow());

        client.when()
                .toolsCall("listAuthenticationFlows", Map.of("realm", "acme"), response -> {
                    JsonNode flows = readTree(text(response));
                    assertEquals(List.of("realm: BROWSER"), strings(withField(flows, "alias", alias).get("usedBy")));
                    assertFalse(strings(withField(flows, "alias", "browser").get("usedBy")).contains("realm: BROWSER"));
                })
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", alias, "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("is in use by realm: BROWSER"), text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertTrue(flowExists(alias));
    }

    @Test
    void bindRealmFlowRefusesAnUnknownAliasASubFlowAndAWrongType() {
        String alias = copyBrowser();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "BROWSER", "flowAlias", "nope", "confirm",
                        true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow nope not found in realm acme", text(response));
                        })
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "BROWSER", "flowAlias", alias + " forms",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow " + alias + " forms not found in realm acme",
                                    text(response));
                        })
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "BROWSER", "flowAlias", "clients",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow clients is a client-flow; BROWSER takes a basic-flow."
                                    + " Nothing was changed.", text(response));
                        })
                .toolsCall("bindRealmFlow", Map.of("realm", "acme", "slot", "CLIENT_AUTHENTICATION", "flowAlias",
                        alias, "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow " + alias + " is a basic-flow; CLIENT_AUTHENTICATION"
                                    + " takes a client-flow. Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        RealmRepresentation realm = acme().toRepresentation();
        assertEquals("browser", realm.getBrowserFlow());
        assertEquals("clients", realm.getClientAuthenticationFlow());
    }

    @Test
    void setClientFlowOverrideSetsAndClearsAnOverride() {
        String alias = copyBrowser();
        String clientId = createClient();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setClientFlowOverride", Map.of("realm", "acme", "clientId", clientId, "binding",
                        "DIRECT_GRANT", "flowAlias", alias, "confirm", false), response -> assertTrue(response.isError()))
                .thenAssertResults();
        assertEquals(Map.of(), overrides(clientId));

        client.when()
                .toolsCall("setClientFlowOverride", Map.of("realm", "acme", "clientId", clientId, "binding",
                        "DIRECT_GRANT", "flowAlias", alias, "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Set the DIRECT_GRANT flow of client " + clientId + " in realm acme to "
                                    + alias, text(response));
                        })
                .thenAssertResults();
        assertEquals(flowId(alias), overrides(clientId).get("direct_grant"));

        client.when()
                .toolsCall("deleteAuthenticationFlow", Map.of("realm", "acme", "alias", alias, "confirm", true),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).contains("is in use by client " + clientId + ": DIRECT_GRANT"),
                                    text(response));
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("setClientFlowOverride", Map.of("realm", "acme", "clientId", clientId, "binding",
                        "DIRECT_GRANT", "confirm", true), response -> {
                            assertFalse(response.isError(), text(response));
                            assertEquals("Cleared the DIRECT_GRANT flow of client " + clientId
                                    + " in realm acme; it uses the realm's flow", text(response));
                        })
                .thenAssertResults();

        client.when()
                .toolsCall("listAuthenticationFlows", Map.of("realm", "acme"), response -> assertTrue(
                        withField(readTree(text(response)), "alias", alias).get("usedBy").isEmpty(), text(response)))
                .thenAssertResults();

        client.disconnect();
        assertTrue(overrides(clientId).getOrDefault("direct_grant", "").isEmpty(), overrides(clientId).toString());
    }

    @Test
    void setClientFlowOverrideRefusesAnUnknownClientAndAClientFlow() {
        String clientId = createClient();
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("setClientFlowOverride", Map.of("realm", "acme", "clientId", "nope", "binding", "BROWSER",
                        "flowAlias", "browser", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Client nope not found in realm acme", text(response));
                        })
                .toolsCall("setClientFlowOverride", Map.of("realm", "acme", "clientId", clientId, "binding",
                        "BROWSER", "flowAlias", "clients", "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertEquals("Authentication flow clients is a client-flow; BROWSER takes a basic-flow."
                                    + " Nothing was changed.", text(response));
                        })
                .thenAssertResults();

        client.disconnect();
        assertEquals(Map.of(), overrides(clientId));
    }

    @Test
    void bindingToolsAreRefusedForMaster() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .toolsCall("bindRealmFlow", Map.of("realm", "master", "slot", "BROWSER", "flowAlias", "browser",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("bindRealmFlow is refused for the master realm"),
                                    text(response));
                        })
                .toolsCall("setClientFlowOverride", Map.of("realm", "master", "clientId", "x", "binding", "BROWSER",
                        "confirm", true), response -> {
                            assertTrue(response.isError());
                            assertTrue(text(response).startsWith("setClientFlowOverride is refused for the master realm"),
                                    text(response));
                        })
                .thenAssertResults();

        client.disconnect();
    }
}
