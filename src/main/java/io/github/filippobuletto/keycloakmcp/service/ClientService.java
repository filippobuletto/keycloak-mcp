package io.github.filippobuletto.keycloakmcp.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.github.filippobuletto.keycloakmcp.model.ClientDetail;
import io.github.filippobuletto.keycloakmcp.model.ClientInput;
import io.github.filippobuletto.keycloakmcp.model.ClientSecret;
import io.github.filippobuletto.keycloakmcp.model.ClientSummary;
import io.github.filippobuletto.keycloakmcp.model.RoleMappings;
import io.github.filippobuletto.keycloakmcp.model.ServiceAccountDetail;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class ClientService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    UserService users;

    public List<ClientSummary> list(String realm) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).clients().findAll().stream()
                .map(ClientService::summary)
                .toList());
    }

    public ClientDetail get(String realm, String clientId) {
        ClientRepresentation client = find(realm, clientId);
        MappingsRepresentation scopeMappings = KeycloakErrors.call(
                () -> keycloak.realm(realm).clients().get(client.getId()).getScopeMappings().getAll());
        return detail(client, UserService.roles(scopeMappings));
    }

    public ServiceAccountDetail serviceAccount(String realm, String clientId) {
        ClientRepresentation client = find(realm, clientId);
        if (!isTrue(client.isServiceAccountsEnabled())) {
            throw new ToolCallException("Client " + clientId + " has no service account in realm " + realm);
        }
        UserRepresentation user = KeycloakErrors.call(
                () -> keycloak.realm(realm).clients().get(client.getId()).getServiceAccountUser());
        List<String> effectiveRealmRoles = KeycloakErrors.call(() -> keycloak.realm(realm).users().get(user.getId())
                .roles().realmLevel().listEffective().stream().map(RoleRepresentation::getName).sorted().toList());
        return new ServiceAccountDetail(user.getId(), user.getUsername(), users.roles(realm, user.getId()),
                effectiveRealmRoles, users.groups(realm, user.getId()));
    }

    /** Looks a client up by clientId; callers needing the internal UUID read it from the result. */
    ClientRepresentation find(String realm, String clientId) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).clients().findByClientId(clientId).stream()
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Client " + clientId + " not found in realm " + realm)));
    }

    public ClientSecret secret(String realm, String clientId) {
        String id = confidential(realm, clientId).getId();
        String secret = KeycloakErrors.call(() -> keycloak.realm(realm).clients().get(id).getSecret().getValue());
        return new ClientSecret(realm, clientId, secret);
    }

    public ClientSecret regenerateSecret(String realm, String clientId) {
        String id = confidential(realm, clientId).getId();
        String secret = KeycloakErrors.call(
                () -> keycloak.realm(realm).clients().get(id).generateNewSecret().getValue());
        return new ClientSecret(realm, clientId, secret);
    }

    /** Public and bearer-only clients have no usable secret, so the secret tools refuse them before calling Keycloak. */
    private ClientRepresentation confidential(String realm, String clientId) {
        ClientRepresentation client = find(realm, clientId);
        if (isTrue(client.isPublicClient())) {
            throw new ToolCallException(clientId + " is a public client and has no secret. Nothing was changed.");
        }
        if (isTrue(client.isBearerOnly())) {
            throw new ToolCallException(clientId + " is a bearer-only client and has no secret. Nothing was changed.");
        }
        return client;
    }

    public ClientDetail create(String realm, String clientId, ClientInput input) {
        ClientRepresentation client = newClient(clientId, input);
        KeycloakErrors.createdId(() -> keycloak.realm(realm).clients().create(client));
        return get(realm, clientId);
    }

    /**
     * A confidential client with service accounts authenticates as itself, so browser login and the password grant
     * stay off unless requested. Other clients keep Keycloak's defaults. A client is confidential unless public is
     * requested, because Keycloak makes a client without {@code publicClient} public.
     */
    static ClientRepresentation newClient(String clientId, ClientInput input) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setProtocol("openid-connect");
        client.setPublicClient(false);
        if (Boolean.TRUE.equals(input.serviceAccountsEnabled()) && !Boolean.TRUE.equals(input.publicClient())) {
            client.setStandardFlowEnabled(false);
            client.setDirectAccessGrantsEnabled(false);
        }
        apply(client, input);
        return client;
    }

    public ClientDetail update(String realm, String clientId, ClientInput input) {
        ClientRepresentation client = find(realm, clientId);
        apply(client, input);
        KeycloakErrors.run(() -> keycloak.realm(realm).clients().get(client.getId()).update(client));
        return get(realm, clientId);
    }

    public void delete(String realm, String clientId) {
        String id = find(realm, clientId).getId();
        KeycloakErrors.run(() -> keycloak.realm(realm).clients().get(id).remove());
    }

    static void apply(ClientRepresentation client, ClientInput input) {
        if (input.name() != null) {
            client.setName(input.name());
        }
        if (input.publicClient() != null) {
            client.setPublicClient(input.publicClient());
        }
        if (input.serviceAccountsEnabled() != null) {
            client.setServiceAccountsEnabled(input.serviceAccountsEnabled());
        }
        if (input.standardFlowEnabled() != null) {
            client.setStandardFlowEnabled(input.standardFlowEnabled());
        }
        if (input.directAccessGrantsEnabled() != null) {
            client.setDirectAccessGrantsEnabled(input.directAccessGrantsEnabled());
        }
        if (input.redirectUris() != null) {
            client.setRedirectUris(input.redirectUris());
        }
        if (input.webOrigins() != null) {
            client.setWebOrigins(input.webOrigins());
        }
    }

    static ClientSummary summary(ClientRepresentation client) {
        return new ClientSummary(client.getId(), client.getClientId(), client.getName(), isTrue(client.isEnabled()),
                client.getProtocol(), isTrue(client.isPublicClient()), isTrue(client.isServiceAccountsEnabled()));
    }

    static ClientDetail detail(ClientRepresentation client, RoleMappings scopeMappings) {
        return new ClientDetail(
                client.getId(),
                client.getClientId(),
                client.getName(),
                client.getDescription(),
                isTrue(client.isEnabled()),
                client.getProtocol(),
                isTrue(client.isPublicClient()),
                isTrue(client.isServiceAccountsEnabled()),
                isTrue(client.isStandardFlowEnabled()),
                isTrue(client.isDirectAccessGrantsEnabled()),
                isTrue(client.isImplicitFlowEnabled()),
                isTrue(client.isFullScopeAllowed()),
                client.getClientAuthenticatorType(),
                client.getRootUrl(),
                client.getBaseUrl(),
                client.getRedirectUris() == null ? List.of() : client.getRedirectUris(),
                client.getWebOrigins() == null ? List.of() : client.getWebOrigins(),
                safeAttributes(client.getAttributes()),
                ClientScopeService.mappers(client.getProtocolMappers()),
                scopeMappings);
    }

    /**
     * OIDC and SAML client settings safe to show. Attributes can also carry credentials (for example
     * {@code client.secret.rotated}), so anything not listed is dropped; extend the list when a tool needs a setting.
     */
    static final Set<String> SAFE_ATTRIBUTES = Set.of(
            "realm_client",
            "pkce.code.challenge.method",
            "post.logout.redirect.uris",
            "client.use.lightweight.access.token.enabled",
            "access.token.lifespan",
            "client.session.idle.timeout",
            "client.session.max.lifespan",
            "client.offline.session.idle.timeout",
            "client.offline.session.max.lifespan",
            "use.refresh.tokens",
            "client_credentials.use_refresh_token",
            "oauth2.device.authorization.grant.enabled",
            "oidc.ciba.grant.enabled",
            "standard.token.exchange.enabled",
            "require.pushed.authorization.requests",
            "exclude.session.state.from.auth.response",
            "tls.client.certificate.bound.access.tokens",
            "access.token.signed.response.alg",
            "id.token.signed.response.alg",
            "frontchannel.logout.url",
            "frontchannel.logout.session.required",
            "backchannel.logout.url",
            "backchannel.logout.session.required",
            "backchannel.logout.revoke.offline.tokens",
            "login_theme",
            "display.on.consent.screen",
            "consent.screen.text",
            // SAML settings an audit reads; Keycloak omits a flag that was never set, which means false
            "saml.assertion.signature",
            "saml.client.signature",
            "saml.encrypt",
            "saml.onetimeuse.condition",
            "saml.signature.algorithm");

    static Map<String, String> safeAttributes(Map<String, String> attributes) {
        Map<String, String> safe = new TreeMap<>();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (SAFE_ATTRIBUTES.contains(key)) {
                    safe.put(key, value);
                }
            });
        }
        return safe;
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
