package io.github.filippobuletto.keycloakmcp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.ProtocolMapperTypeRepresentation;

import io.github.filippobuletto.keycloakmcp.model.ClientScopeDetail;
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class ClientScopeMapperService {

    private static final String OIDC = "openid-connect";

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientScopeService scopes;

    public List<ProtocolMapperType> types() {
        Map<String, String> messages = messages();
        return oidcTypes().stream().map(type -> type(type, messages)).toList();
    }

    /** Keycloak sends most property labels as admin console message keys; the console's bundle resolves them. */
    private Map<String, String> messages() {
        try {
            Map<String, String> messages = new HashMap<>();
            for (Map<String, String> entry : keycloak.proxy(AdminMessagesResource.class).messages("master", "en")) {
                messages.put(entry.get("key"), entry.get("value"));
            }
            return messages;
        } catch (RuntimeException e) {
            // Labels are help text only, so a missing bundle leaves the keys in place rather than failing the call
            return Map.of();
        }
    }

    public ClientScopeDetail add(String realm, String scope, String name, String mapperType,
            Map<String, String> config) {
        String scopeId = scopes.oidcScope(realm, scope).getId();
        ProtocolMapperTypeRepresentation type = oidcType(mapperType);
        checkConfig(type, config.keySet());
        checkRequired(type, config.keySet());
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName(name);
        mapper.setProtocol(OIDC);
        mapper.setProtocolMapper(mapperType);
        mapper.setConfig(new HashMap<>(config));
        KeycloakErrors.createdId(
                () -> keycloak.realm(realm).clientScopes().get(scopeId).getProtocolMappers().createMapper(mapper));
        return scopes.get(realm, scope);
    }

    /** Merges {@code config} into the mapper's config: supplied keys change, the others keep their values. */
    public ClientScopeDetail update(String realm, String scope, String name, Map<String, String> config) {
        ClientScopeRepresentation found = scopes.oidcScope(realm, scope);
        ProtocolMapperRepresentation mapper = find(realm, found, name);
        checkConfig(oidcType(mapper.getProtocolMapper()), config.keySet());
        Map<String, String> merged = mapper.getConfig() == null ? new HashMap<>() : new HashMap<>(mapper.getConfig());
        merged.putAll(config);
        mapper.setConfig(merged);
        KeycloakErrors.run(() -> keycloak.realm(realm).clientScopes().get(found.getId()).getProtocolMappers()
                .update(mapper.getId(), mapper));
        return scopes.get(realm, scope);
    }

    public void delete(String realm, String scope, String name) {
        ClientScopeRepresentation found = scopes.oidcScope(realm, scope);
        String id = find(realm, found, name).getId();
        KeycloakErrors.run(() -> keycloak.realm(realm).clientScopes().get(found.getId()).getProtocolMappers()
                .delete(id));
    }

    private static ProtocolMapperRepresentation find(String realm, ClientScopeRepresentation scope, String name) {
        return (scope.getProtocolMappers() == null ? List.<ProtocolMapperRepresentation> of()
                : scope.getProtocolMappers()).stream()
                .filter(mapper -> mapper.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Protocol mapper " + name + " not found in client scope "
                        + scope.getName() + " of realm " + realm));
    }

    /** Checks only the supplied keys: built-in mappers hold keys their type no longer declares. */
    static void checkConfig(ProtocolMapperTypeRepresentation type, Set<String> keys) {
        MapperConfig.check(type.getId(), type.getProperties(), keys);
    }

    private ProtocolMapperTypeRepresentation oidcType(String id) {
        return oidcTypes().stream()
                .filter(type -> type.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Mapper type " + id
                        + " is not an openid-connect mapper type of this server; listProtocolMapperTypes shows them."
                        + " Nothing was changed."));
    }

    private List<ProtocolMapperTypeRepresentation> oidcTypes() {
        return KeycloakErrors.call(() -> keycloak.serverInfo().getInfo().getProtocolMapperTypes()
                .getOrDefault(OIDC, List.of()));
    }

    /** Keycloak marks some keys required but stores a mapper without them, which then maps nothing. */
    static void checkRequired(ProtocolMapperTypeRepresentation type, Set<String> keys) {
        if (type.getProperties() == null) {
            return;
        }
        for (ConfigPropertyRepresentation property : type.getProperties()) {
            if (property.isRequired() && !keys.contains(property.getName())) {
                throw new ToolCallException("Config key " + property.getName() + " is required by mapper type "
                        + type.getId() + ". Nothing was changed.");
            }
        }
    }

    private static ProtocolMapperType type(ProtocolMapperTypeRepresentation type, Map<String, String> messages) {
        List<ProtocolMapperType.Property> properties = type.getProperties() == null ? List.of()
                : type.getProperties().stream()
                        .map(p -> new ProtocolMapperType.Property(p.getName(),
                                p.getLabel() == null ? null : messages.getOrDefault(p.getLabel(), p.getLabel()),
                                p.getType(), p.isRequired(), p.getDefaultValue(), p.getOptions(),
                                p.getHelpText() == null ? null : messages.getOrDefault(p.getHelpText(), p.getHelpText())))
                        .toList();
        return new ProtocolMapperType(type.getId(), type.getName(), type.getCategory(), type.getHelpText(),
                properties);
    }
}
