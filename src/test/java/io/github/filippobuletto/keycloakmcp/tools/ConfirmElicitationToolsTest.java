package io.github.filippobuletto.keycloakmcp.tools;

import static io.github.filippobuletto.keycloakmcp.tools.ConfirmElicitationTest.accepts;
import static io.github.filippobuletto.keycloakmcp.tools.ConfirmElicitationTest.declines;
import static io.github.filippobuletto.keycloakmcp.tools.ConfirmElicitationTest.elicitingClient;
import static io.github.filippobuletto.keycloakmcp.tools.RealmToolsTest.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * With {@code MCP_CONFIRM_MODE=elicit}: every confirmation other than deleteUser's sends a dialog that names its
 * target and realm, and a decline leaves Keycloak unchanged.
 */
@QuarkusTest
@TestProfile(ElicitProfile.class)
class ConfirmElicitationToolsTest {

    @Inject
    Keycloak keycloak;

    RealmResource acme() {
        return keycloak.realm("acme");
    }

    static String tmp() {
        return "tmp-cf-" + UUID.randomUUID();
    }

    @AfterEach
    void deleteThrowaways() {
        keycloak.realms().findAll().stream().filter(r -> r.getRealm().startsWith("tmp-cf-"))
                .forEach(r -> keycloak.realm(r.getRealm()).remove());
        acme().clients().findAll().stream().filter(c -> c.getClientId().startsWith("tmp-cf-"))
                .forEach(c -> acme().clients().get(c.getId()).remove());
        acme().users().search("tmp-cf-", 0, 1000).stream().filter(u -> u.getUsername().startsWith("tmp-cf-"))
                .forEach(u -> acme().users().delete(u.getId()).close());
        acme().roles().list().stream().filter(r -> r.getName().startsWith("tmp-cf-"))
                .forEach(r -> acme().roles().deleteRole(r.getName()));
        acme().groups().groups(null, 0, 1000, true).stream().filter(g -> g.getName().startsWith("tmp-cf-"))
                .forEach(g -> acme().groups().group(g.getId()).remove());
        acme().identityProviders().findAll().stream().filter(p -> p.getAlias().startsWith("tmp-cf-"))
                .forEach(p -> acme().identityProviders().get(p.getAlias()).remove());
        AuthenticationFlowToolsTest.restoreBindings(acme(), "tmp-cf-");
        acme().flows().getFlows().stream().filter(f -> f.getAlias().startsWith("tmp-cf-"))
                .forEach(f -> acme().flows().deleteFlow(f.getId()));
        List<String> defaults = acme().getDefaultDefaultClientScopes().stream()
                .filter(s -> s.getName().startsWith("tmp-cf-")).map(ClientScopeRepresentation::getId).toList();
        defaults.forEach(id -> acme().removeDefaultDefaultClientScope(id));
        acme().clientScopes().findAll().stream().filter(s -> s.getName().startsWith("tmp-cf-"))
                .forEach(s -> acme().clientScopes().get(s.getId()).remove());
    }

    String createRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(tmp());
        realm.setEnabled(true);
        keycloak.realms().create(realm);
        return realm.getRealm();
    }

    boolean realmExists(String name) {
        return keycloak.realms().findAll().stream().anyMatch(r -> r.getRealm().equals(name));
    }

    String createClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(tmp());
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        acme().clients().create(client).close();
        return client.getClientId();
    }

    String clientUuid(String clientId) {
        return acme().clients().findByClientId(clientId).getFirst().getId();
    }

    boolean clientExists(String clientId) {
        return !acme().clients().findByClientId(clientId).isEmpty();
    }

    String createScope() {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName(tmp());
        scope.setProtocol("openid-connect");
        try (var response = acme().clientScopes().create(scope)) {
            CreatedResponseUtil.getCreatedId(response);
        }
        return scope.getName();
    }

    ClientScopeRepresentation scope(String name) {
        return acme().clientScopes().findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
    }

    String createProvider() {
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias(tmp());
        provider.setProviderId("oidc");
        provider.setEnabled(true);
        provider.setConfig(new HashMap<>(Map.of("clientId", "acme-broker", "authorizationUrl",
                "https://idp.example/auth", "tokenUrl", "https://idp.example/token")));
        acme().identityProviders().create(provider).close();
        return provider.getAlias();
    }

    boolean providerExists(String alias) {
        return acme().identityProviders().findAll().stream().anyMatch(p -> p.getAlias().equals(alias));
    }

    String addProviderMapper(String alias) {
        IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
        mapper.setName("department");
        mapper.setIdentityProviderAlias(alias);
        mapper.setIdentityProviderMapper("hardcoded-attribute-idp-mapper");
        mapper.setConfig(new HashMap<>(Map.of("attribute", "department", "attribute.value", "sales", "syncMode",
                "INHERIT")));
        acme().identityProviders().get(alias).addMapper(mapper).close();
        return mapper.getName();
    }

    boolean providerMapperExists(String alias, String name) {
        return acme().identityProviders().get(alias).getMappers().stream().anyMatch(m -> m.getName().equals(name));
    }

    @Test
    void createRealmDeclined() {
        String realm = "tmp-cf-" + UUID.randomUUID();

        declines("createRealm", Map.of("realm", realm), realm);

        assertFalse(realmExists(realm));
    }

    @Test
    void deleteRealmDeclined() {
        String realm = createRealm();

        declines("deleteRealm", Map.of("realm", realm), realm);

        assertTrue(realmExists(realm));
    }

    @Test
    void deleteClientDeclined() {
        String clientId = createClient();

        declines("deleteClient", Map.of("realm", "acme", "clientId", clientId), clientId, "acme");

        assertTrue(clientExists(clientId));
    }

    @Test
    void getClientSecretDeclined() {
        String clientId = createClient();
        String before = acme().clients().get(clientUuid(clientId)).getSecret().getValue();

        declines("getClientSecret", Map.of("realm", "acme", "clientId", clientId), clientId, "acme");

        assertEquals(before, acme().clients().get(clientUuid(clientId)).getSecret().getValue());
    }

    @Test
    void regenerateClientSecretDeclined() {
        String clientId = createClient();
        String before = acme().clients().get(clientUuid(clientId)).getSecret().getValue();

        declines("regenerateClientSecret", Map.of("realm", "acme", "clientId", clientId), clientId, "acme");

        assertEquals(before, acme().clients().get(clientUuid(clientId)).getSecret().getValue());
    }

    @Test
    void deleteRealmRoleDeclined() {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(tmp());
        acme().roles().create(role);

        declines("deleteRealmRole", Map.of("realm", "acme", "name", role.getName()), role.getName(), "acme");

        assertTrue(acme().roles().list().stream().anyMatch(r -> r.getName().equals(role.getName())));
    }

    @Test
    void deleteGroupDeclinedThenAccepted() {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(tmp());
        acme().groups().add(group).close();
        String path = "/" + group.getName();

        declines("deleteGroup", Map.of("realm", "acme", "path", path), path, "acme");
        assertTrue(acme().groups().groups(group.getName(), 0, 10).stream().anyMatch(g -> g.getPath().equals(path)));

        accepts("deleteGroup", Map.of("realm", "acme", "path", path));
        assertFalse(acme().groups().groups(group.getName(), 0, 10).stream().anyMatch(g -> g.getPath().equals(path)));
    }

    @Test
    void deleteClientScopeDeclined() {
        String name = createScope();

        declines("deleteClientScope", Map.of("realm", "acme", "name", name), name, "acme");

        assertTrue(scope(name) != null);
    }

    @Test
    void setRealmDefaultClientScopeDeclined() {
        String name = createScope();

        declines("setRealmDefaultClientScope", Map.of("realm", "acme", "name", name, "type", "default"), name,
                "default", "acme");

        assertFalse(acme().getDefaultDefaultClientScopes().stream().anyMatch(s -> s.getName().equals(name)));
    }

    @Test
    void deleteClientScopeMapperDeclined() {
        String name = createScope();
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("tier");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        mapper.setConfig(Map.of("claim.name", "tier", "claim.value", "gold", "access.token.claim", "true"));
        acme().clientScopes().get(scope(name).getId()).getProtocolMappers().createMapper(mapper).close();

        declines("deleteClientScopeMapper", Map.of("realm", "acme", "scope", name, "name", "tier"), "tier", name,
                "acme");

        assertTrue(acme().clientScopes().get(scope(name).getId()).getProtocolMappers().getMappers().stream()
                .anyMatch(m -> m.getName().equals("tier")));
    }

    @Test
    void deleteIdentityProviderDeclined() {
        String alias = createProvider();

        declines("deleteIdentityProvider", Map.of("realm", "acme", "alias", alias), alias, "acme");

        assertTrue(providerExists(alias));
    }

    @Test
    void deleteIdentityProviderMapperDeclinedThenAccepted() {
        String alias = createProvider();
        String name = addProviderMapper(alias);
        Map<String, Object> args = Map.of("realm", "acme", "alias", alias, "name", name);

        declines("deleteIdentityProviderMapper", args, name, alias, "acme");
        assertTrue(providerMapperExists(alias, name));

        accepts("deleteIdentityProviderMapper", args);
        assertFalse(providerMapperExists(alias, name));
    }

    @Test
    void disablingAProviderDeclinedThenAccepted() {
        String alias = createProvider();
        Map<String, Object> args = Map.of("realm", "acme", "alias", alias, "enabled", false);

        declines("updateIdentityProvider", args, alias, "acme");
        assertTrue(acme().identityProviders().get(alias).toRepresentation().isEnabled());

        accepts("updateIdentityProvider", args);
        assertFalse(acme().identityProviders().get(alias).toRepresentation().isEnabled());
    }

    @Test
    void updatingAProviderWithoutDisablingSendsNoDialog() {
        String alias = createProvider();
        McpStreamableTestClient client = elicitingClient();

        client.when()
                .toolsCall("updateIdentityProvider", Map.of("realm", "acme", "alias", alias, "displayName", "Corp"),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();

        assertEquals(List.of(), client.snapshot().requests());
        client.disconnect();
        assertEquals("Corp", acme().identityProviders().get(alias).toRepresentation().getDisplayName());
    }

    boolean flowExists(String alias) {
        return acme().flows().getFlows().stream().anyMatch(f -> f.getAlias().equals(alias));
    }

    @Test
    void deleteAuthenticationFlowDeclinedThenAccepted() {
        String alias = tmp();
        acme().flows().copy("browser", Map.of("newName", alias)).close();
        Map<String, Object> args = Map.of("realm", "acme", "alias", alias);

        declines("deleteAuthenticationFlow", args, alias, "acme");
        assertTrue(flowExists(alias));

        accepts("deleteAuthenticationFlow", args);
        assertFalse(flowExists(alias));
    }

    @Test
    void deleteFlowExecutionDeclinedThenAccepted() {
        String alias = tmp();
        acme().flows().copy("browser", Map.of("newName", alias)).close();
        String cookie = acme().flows().getExecutions(alias).getFirst().getId();
        Map<String, Object> args = Map.of("realm", "acme", "flowAlias", alias, "executionId", cookie);

        declines("deleteFlowExecution", args, "Cookie", alias, "acme");
        assertEquals(cookie, acme().flows().getExecutions(alias).getFirst().getId());

        accepts("deleteFlowExecution", args);
        assertTrue(acme().flows().getExecutions(alias).stream().noneMatch(e -> e.getId().equals(cookie)));
    }

    @Test
    void deleteExecutionConfigDeclinedThenAccepted() {
        String alias = tmp();
        acme().flows().copy("browser", Map.of("newName", alias)).close();
        String condition = acme().flows().getExecutions(alias).stream()
                .filter(e -> "conditional-credential".equals(e.getProviderId())).findFirst().orElseThrow().getId();
        Map<String, Object> args = Map.of("realm", "acme", "flowAlias", alias, "executionId", condition);

        declines("deleteExecutionConfig", args, "Condition - credential", alias, "acme");
        assertTrue(hasConfig(alias, condition));

        accepts("deleteExecutionConfig", args);
        assertFalse(hasConfig(alias, condition));
    }

    boolean hasConfig(String alias, String executionId) {
        return acme().flows().getExecutions(alias).stream()
                .anyMatch(e -> e.getId().equals(executionId) && e.getAuthenticationConfig() != null);
    }

    @Test
    void bindRealmFlowDeclinedThenAccepted() {
        String alias = tmp();
        acme().flows().copy("direct grant", Map.of("newName", alias)).close();
        Map<String, Object> args = Map.of("realm", "acme", "slot", "DIRECT_GRANT", "flowAlias", alias);

        declines("bindRealmFlow", args, alias, "DIRECT_GRANT", "acme");
        assertEquals("direct grant", acme().toRepresentation().getDirectGrantFlow());

        accepts("bindRealmFlow", args);
        assertEquals(alias, acme().toRepresentation().getDirectGrantFlow());
    }

    @Test
    void setClientFlowOverrideDeclinedThenAccepted() {
        String alias = tmp();
        acme().flows().copy("browser", Map.of("newName", alias)).close();
        String clientId = createClient();
        Map<String, Object> args = Map.of("realm", "acme", "clientId", clientId, "binding", "BROWSER", "flowAlias",
                alias);

        declines("setClientFlowOverride", args, clientId, alias, "acme");
        assertTrue(browserOverride(clientId).isEmpty());

        accepts("setClientFlowOverride", args);
        assertFalse(browserOverride(clientId).isEmpty());
    }

    @Test
    void disablingARequiredActionDeclinedThenAccepted() {
        RequiredActionProviderRepresentation before = acme().flows().getRequiredAction("VERIFY_PROFILE");
        Map<String, Object> args = Map.of("realm", "acme", "alias", "VERIFY_PROFILE", "enabled", false);
        try {
            declines("updateRequiredAction", args, "VERIFY_PROFILE", "acme");
            assertTrue(acme().flows().getRequiredAction("VERIFY_PROFILE").isEnabled());

            accepts("updateRequiredAction", args);
            assertFalse(acme().flows().getRequiredAction("VERIFY_PROFILE").isEnabled());
        } finally {
            acme().flows().updateRequiredAction("VERIFY_PROFILE", before);
        }
    }

    @Test
    void enablingARequiredActionSendsNoDialog() {
        McpStreamableTestClient client = elicitingClient();

        client.when()
                .toolsCall("updateRequiredAction", Map.of("realm", "acme", "alias", "VERIFY_PROFILE", "enabled", true),
                        response -> assertFalse(response.isError(), text(response)))
                .thenAssertResults();

        assertEquals(List.of(), client.snapshot().requests());
        client.disconnect();
    }

    @Test
    void turningEventsOffDeclinedThenAccepted() {
        RealmEventsConfigRepresentation before = acme().getRealmEventsConfig();
        try {
            RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
            on.setEventsEnabled(true);
            on.setEnabledEventTypes(null);
            acme().updateRealmEventsConfig(on);
            Map<String, Object> args = Map.of("realm", "acme", "eventsEnabled", false);

            declines("updateEventSettings", args, "acme", "login events off");
            assertTrue(acme().getRealmEventsConfig().isEventsEnabled());

            accepts("updateEventSettings", args);
            assertFalse(acme().getRealmEventsConfig().isEventsEnabled());
        } finally {
            EventToolsTest.restore(acme(), before);
        }
    }

    @Test
    void turningEventsOnSendsNoDialog() {
        RealmEventsConfigRepresentation before = acme().getRealmEventsConfig();
        McpStreamableTestClient client = elicitingClient();
        try {
            client.when()
                    .toolsCall("updateEventSettings", Map.of("realm", "acme", "eventsEnabled", true),
                            response -> assertFalse(response.isError(), text(response)))
                    .thenAssertResults();

            assertEquals(List.of(), client.snapshot().requests());
        } finally {
            client.disconnect();
            EventToolsTest.restore(acme(), before);
        }
    }

    @Test
    void clearEventsDeclinedThenAccepted() {
        RealmEventsConfigRepresentation before = acme().getRealmEventsConfig();
        try {
            RealmEventsConfigRepresentation on = acme().getRealmEventsConfig();
            on.setEventsEnabled(true);
            on.setEnabledEventTypes(null);
            acme().updateRealmEventsConfig(on);
            signedInUser(tmp());
            Map<String, Object> args = Map.of("realm", "acme", "kind", "LOGIN");

            declines("clearEvents", args, "acme", "login events");
            assertFalse(acme().getEvents().isEmpty());

            accepts("clearEvents", args);
            assertTrue(acme().getEvents().isEmpty());
        } finally {
            EventToolsTest.restore(acme(), before);
        }
    }

    /** A tmp-cf user signed in once through a tmp-cf client; returns the user's id. */
    String signedInUser(String username) {
        String clientId = tmp();
        SessionToolsTest.createClient(acme(), clientId);
        String userId = SessionToolsTest.createUser(acme(), username);
        SessionToolsTest.signIn(clientId, username, false);
        return userId;
    }

    @Test
    void logoutUserDeclinedThenAccepted() {
        String username = tmp();
        String userId = signedInUser(username);
        Map<String, Object> args = Map.of("realm", "acme", "userId", userId);

        declines("logoutUser", args, username, "acme", "1 regular and 0 offline");
        assertEquals(1, acme().users().get(userId).getUserSessions().size());

        accepts("logoutUser", args);
        assertTrue(acme().users().get(userId).getUserSessions().isEmpty());
    }

    @Test
    void logoutRealmDeclined() {
        String userId = signedInUser(tmp());

        declines("logoutRealm", Map.of("realm", "acme"), "acme");

        assertEquals(1, acme().users().get(userId).getUserSessions().size());
    }

    String browserOverride(String clientId) {
        Map<String, String> overrides = acme().clients().findByClientId(clientId).getFirst()
                .getAuthenticationFlowBindingOverrides();
        return overrides == null ? "" : overrides.getOrDefault("browser", "");
    }
}
