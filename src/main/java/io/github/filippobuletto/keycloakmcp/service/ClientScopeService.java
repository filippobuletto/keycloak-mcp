package io.github.filippobuletto.keycloakmcp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import io.github.filippobuletto.keycloakmcp.model.ClientClientScopes;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeDetail;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeInput;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeSummary;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class ClientScopeService {

    /** The scopes Keycloak 26.8.2 creates in every new realm, measured by ClientScopeToolsTest. */
    public static final Set<String> BUILT_IN = Set.of(
            "AuthnContextClassRef", "acr", "address", "basic", "email", "kc-admin-api", "microprofile-jwt",
            "offline_access", "organization", "phone", "profile", "role_list", "roles", "saml_organization",
            "service_account", "web-origins");

    private static final String OIDC = "openid-connect";

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientService clients;

    public List<ClientScopeSummary> list(String realm) {
        return KeycloakErrors.call(() -> {
            Set<String> defaults = ids(keycloak.realm(realm).getDefaultDefaultClientScopes());
            Set<String> optional = ids(keycloak.realm(realm).getDefaultOptionalClientScopes());
            return keycloak.realm(realm).clientScopes().findAll().stream()
                    .map(scope -> new ClientScopeSummary(scope.getId(), scope.getName(), scope.getDescription(),
                            scope.getProtocol(), defaults.contains(scope.getId()) ? "default"
                                    : optional.contains(scope.getId()) ? "optional" : "none"))
                    .toList();
        });
    }

    public ClientScopeDetail get(String realm, String name) {
        ClientScopeRepresentation scope = find(realm, name);
        MappingsRepresentation roles = KeycloakErrors.call(
                () -> keycloak.realm(realm).clientScopes().get(scope.getId()).getScopeMappings().getAll());
        return detail(scope, roles);
    }

    /** Keycloak has no lookup by name for client scopes, so this filters the realm's list. */
    ClientScopeRepresentation find(String realm, String name) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).clientScopes().findAll().stream()
                .filter(scope -> scope.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Client scope " + name + " not found in realm " + realm)));
    }

    /** Write tools act on OIDC scopes only, so a SAML scope is refused before anything reaches Keycloak. */
    ClientScopeRepresentation oidcScope(String realm, String name) {
        ClientScopeRepresentation scope = find(realm, name);
        if (!OIDC.equals(scope.getProtocol())) {
            throw new ToolCallException("Client scope " + name + " uses protocol " + scope.getProtocol()
                    + "; only openid-connect scopes can be changed. Nothing was changed.");
        }
        return scope;
    }

    public ClientScopeDetail create(String realm, String name, ClientScopeInput input) {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName(name);
        scope.setProtocol(OIDC);
        scope.setAttributes(Map.of("include.in.token.scope", "true", "display.on.consent.screen", "true"));
        apply(scope, input);
        KeycloakErrors.createdId(() -> keycloak.realm(realm).clientScopes().create(scope));
        return get(realm, name);
    }

    public ClientScopeDetail update(String realm, String name, ClientScopeInput input) {
        ClientScopeRepresentation scope = oidcScope(realm, name);
        apply(scope, input);
        KeycloakErrors.run(() -> keycloak.realm(realm).clientScopes().get(scope.getId()).update(scope));
        return get(realm, name);
    }

    public void delete(String realm, String name) {
        String id = oidcScope(realm, name).getId();
        if (BUILT_IN.contains(name)) {
            throw new ToolCallException("Client scope " + name + " is built into Keycloak, and deleting it would change"
                    + " every token in realm " + realm + ". Nothing was changed.");
        }
        KeycloakErrors.run(() -> keycloak.realm(realm).clientScopes().get(id).remove());
    }

    static void apply(ClientScopeRepresentation scope, ClientScopeInput input) {
        Map<String, String> attributes = scope.getAttributes() == null ? new HashMap<>()
                : new HashMap<>(scope.getAttributes());
        if (input.description() != null) {
            scope.setDescription(input.description());
        }
        if (input.includeInTokenScope() != null) {
            attributes.put("include.in.token.scope", input.includeInTokenScope().toString());
        }
        if (input.displayOnConsentScreen() != null) {
            attributes.put("display.on.consent.screen", input.displayOnConsentScreen().toString());
        }
        if (input.consentScreenText() != null) {
            attributes.put("consent.screen.text", input.consentScreenText());
        }
        scope.setAttributes(attributes);
    }

    /** Sets what new clients get ({@code default}, {@code optional} or {@code none}); existing clients keep theirs. */
    public ClientScopeSummary setRealmDefault(String realm, String name, String type) {
        String id = oidcScope(realm, name).getId();
        RealmResource resource = keycloak.realm(realm);
        KeycloakErrors.run(() -> {
            if (ids(resource.getDefaultDefaultClientScopes()).contains(id)) {
                resource.removeDefaultDefaultClientScope(id);
            }
            if (ids(resource.getDefaultOptionalClientScopes()).contains(id)) {
                resource.removeDefaultOptionalClientScope(id);
            }
            if ("default".equals(type)) {
                resource.addDefaultDefaultClientScope(id);
            } else if ("optional".equals(type)) {
                resource.addDefaultOptionalClientScope(id);
            }
        });
        return list(realm).stream().filter(scope -> scope.id().equals(id)).findFirst().orElseThrow();
    }

    /** Maps a realm role, or a role of {@code clientId} when given, to the scope. */
    public ClientScopeDetail addRole(String realm, String name, String role, String clientId) {
        return changeRole(realm, name, role, clientId, true);
    }

    public ClientScopeDetail removeRole(String realm, String name, String role, String clientId) {
        return changeRole(realm, name, role, clientId, false);
    }

    private ClientScopeDetail changeRole(String realm, String name, String role, String clientId, boolean add) {
        RoleMappingResource mappings = keycloak.realm(realm).clientScopes().get(oidcScope(realm, name).getId())
                .getScopeMappings();
        RoleScopeResource target;
        RoleRepresentation found;
        if (clientId == null) {
            target = mappings.realmLevel();
            found = realmRole(realm, role);
        } else {
            String clientUuid = clients.find(realm, clientId).getId();
            target = mappings.clientLevel(clientUuid);
            found = clientRole(realm, clientId, clientUuid, role);
        }
        KeycloakErrors.run(() -> {
            if (add) {
                target.add(List.of(found));
            } else {
                target.remove(List.of(found));
            }
        });
        return get(realm, name);
    }

    private RoleRepresentation realmRole(String realm, String role) {
        return KeycloakErrors.call(() -> {
            try {
                return keycloak.realm(realm).roles().get(role).toRepresentation();
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 404) {
                    throw e;
                }
                throw new ToolCallException("Role " + role + " not found in realm " + realm);
            }
        });
    }

    private RoleRepresentation clientRole(String realm, String clientId, String clientUuid, String role) {
        return KeycloakErrors.call(() -> {
            try {
                return keycloak.realm(realm).clients().get(clientUuid).roles().get(role).toRepresentation();
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 404) {
                    throw e;
                }
                throw new ToolCallException("Role " + role + " not found in client " + clientId + " of realm " + realm);
            }
        });
    }

    public ClientClientScopes forClient(String realm, String clientId) {
        ClientResource client = client(realm, clientId);
        return KeycloakErrors.call(() -> new ClientClientScopes(clientId, names(client.getDefaultClientScopes()),
                names(client.getOptionalClientScopes())));
    }

    /** Adds the scope as {@code default} or {@code optional}, moving it if the client has it as the other type. */
    public ClientClientScopes addToClient(String realm, String clientId, String name, String type) {
        ClientResource client = client(realm, clientId);
        String id = oidcScope(realm, name).getId();
        removeFromClient(client, id);
        KeycloakErrors.run(() -> {
            if ("default".equals(type)) {
                client.addDefaultClientScope(id);
            } else {
                client.addOptionalClientScope(id);
            }
        });
        return forClient(realm, clientId);
    }

    /** Removes the scope whichever type it has; a scope the client lacks is left alone. */
    public ClientClientScopes removeFromClient(String realm, String clientId, String name) {
        ClientResource client = client(realm, clientId);
        removeFromClient(client, oidcScope(realm, name).getId());
        return forClient(realm, clientId);
    }

    private static void removeFromClient(ClientResource client, String id) {
        KeycloakErrors.run(() -> {
            if (ids(client.getDefaultClientScopes()).contains(id)) {
                client.removeDefaultClientScope(id);
            }
            if (ids(client.getOptionalClientScopes()).contains(id)) {
                client.removeOptionalClientScope(id);
            }
        });
    }

    private ClientResource client(String realm, String clientId) {
        return keycloak.realm(realm).clients().get(clients.find(realm, clientId).getId());
    }

    static ClientScopeDetail detail(ClientScopeRepresentation scope, MappingsRepresentation roles) {
        Map<String, String> attributes = scope.getAttributes() == null ? Map.of() : scope.getAttributes();
        Map<String, List<String>> clientRoles = new TreeMap<>();
        if (roles.getClientMappings() != null) {
            for (ClientMappingsRepresentation mapping : roles.getClientMappings().values()) {
                clientRoles.put(mapping.getClient(), roleNames(mapping.getMappings()));
            }
        }
        return new ClientScopeDetail(
                scope.getId(),
                scope.getName(),
                scope.getDescription(),
                scope.getProtocol(),
                // Keycloak treats a missing attribute as true for both settings
                !"false".equals(attributes.get("include.in.token.scope")),
                !"false".equals(attributes.get("display.on.consent.screen")),
                attributes.get("consent.screen.text"),
                attributes.get("gui.order"),
                mappers(scope.getProtocolMappers()),
                roleNames(roles.getRealmMappings()),
                clientRoles);
    }

    /** Mappers with their type id and config, as {@code getClientScope} and {@code getClient} show them. */
    static List<ClientScopeDetail.Mapper> mappers(List<ProtocolMapperRepresentation> mappers) {
        return mappers == null ? List.of()
                : mappers.stream()
                        .map(m -> new ClientScopeDetail.Mapper(m.getName(), m.getProtocolMapper(),
                                m.getConfig() == null ? Map.of() : new TreeMap<>(m.getConfig())))
                        .toList();
    }

    private static Set<String> ids(List<ClientScopeRepresentation> scopes) {
        return scopes.stream().map(ClientScopeRepresentation::getId).collect(Collectors.toSet());
    }

    private static List<String> names(List<ClientScopeRepresentation> scopes) {
        return scopes.stream().map(ClientScopeRepresentation::getName).sorted().toList();
    }

    private static List<String> roleNames(List<RoleRepresentation> roles) {
        return roles == null ? List.of() : roles.stream().map(RoleRepresentation::getName).sorted().toList();
    }
}
