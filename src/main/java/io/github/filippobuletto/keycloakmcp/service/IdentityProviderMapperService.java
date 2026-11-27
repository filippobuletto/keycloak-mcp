package io.github.filippobuletto.keycloakmcp.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperTypeRepresentation;

import io.github.filippobuletto.keycloakmcp.model.IdentityProviderDetail;
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.github.filippobuletto.keycloakmcp.model.SyncMode;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class IdentityProviderMapperService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    IdentityProviderService providers;

    /** The mapper types Keycloak offers for this provider, filtered by its type; Keycloak sends English labels. */
    public List<ProtocolMapperType> types(String realm, String alias) {
        return mapperTypes(realm, alias).values().stream()
                .map(IdentityProviderMapperService::type)
                .sorted(Comparator.comparing(ProtocolMapperType::id))
                .toList();
    }

    /**
     * Keycloak 26.8.2 stores a mapper of any type and any sync mode, so the type, the keys and the sync mode are checked
     * here before the write.
     */
    public IdentityProviderDetail add(String realm, String alias, String name, String mapperType,
            Map<String, String> config, SyncMode syncMode) {
        IdentityProviderMapperTypeRepresentation type = fittingType(realm, alias, mapperType);
        checkKeys(type, config);
        IdentityProviderMapperRepresentation mapper = new IdentityProviderMapperRepresentation();
        mapper.setName(name);
        mapper.setIdentityProviderAlias(alias);
        mapper.setIdentityProviderMapper(mapperType);
        Map<String, String> stored = new HashMap<>(config);
        stored.put(SyncMode.CONFIG_KEY, syncMode.name());
        mapper.setConfig(stored);
        KeycloakErrors.checked(() -> keycloak.realm(realm).identityProviders().get(alias).addMapper(mapper));
        return providers.get(realm, alias);
    }

    /**
     * Merges {@code config} into the mapper's config and sets {@code syncMode} when given; other keys keep their
     * values.
     */
    public IdentityProviderDetail update(String realm, String alias, String name, Map<String, String> config,
            SyncMode syncMode) {
        IdentityProviderMapperRepresentation mapper = find(realm, alias, name);
        checkKeys(fittingType(realm, alias, mapper.getIdentityProviderMapper()), config);
        Map<String, String> merged = mapper.getConfig() == null ? new HashMap<>() : new HashMap<>(mapper.getConfig());
        merged.putAll(config);
        if (syncMode != null) {
            merged.put(SyncMode.CONFIG_KEY, syncMode.name());
        }
        mapper.setConfig(merged);
        KeycloakErrors.run(() -> keycloak.realm(realm).identityProviders().get(alias).update(mapper.getId(), mapper));
        return providers.get(realm, alias);
    }

    public void delete(String realm, String alias, String name) {
        String id = find(realm, alias, name).getId();
        KeycloakErrors.run(() -> keycloak.realm(realm).identityProviders().get(alias).delete(id));
    }

    private IdentityProviderMapperRepresentation find(String realm, String alias, String name) {
        providers.find(realm, alias);
        return KeycloakErrors.call(() -> keycloak.realm(realm).identityProviders().get(alias).getMappers()).stream()
                .filter(mapper -> mapper.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Identity provider mapper " + name
                        + " not found in identity provider " + alias + " of realm " + realm));
    }

    private static void checkKeys(IdentityProviderMapperTypeRepresentation type, Map<String, String> config) {
        if (config.containsKey(SyncMode.CONFIG_KEY)) {
            throw new ToolCallException("Set the sync mode with the syncMode parameter, not as a config key."
                    + " Nothing was changed.");
        }
        MapperConfig.check(type.getId(), type.getProperties(), config.keySet());
    }

    private IdentityProviderMapperTypeRepresentation fittingType(String realm, String alias, String id) {
        Map<String, IdentityProviderMapperTypeRepresentation> types = mapperTypes(realm, alias);
        IdentityProviderMapperTypeRepresentation type = types.get(id);
        if (type == null) {
            throw new ToolCallException("Mapper type " + id + " cannot be used by identity provider " + alias
                    + " of realm " + realm + "; valid types: " + String.join(", ", new TreeSet<>(types.keySet()))
                    + ". Nothing was changed.");
        }
        return type;
    }

    /** Fails naming the alias when the provider does not exist. */
    private Map<String, IdentityProviderMapperTypeRepresentation> mapperTypes(String realm, String alias) {
        providers.find(realm, alias);
        return KeycloakErrors.call(() -> keycloak.realm(realm).identityProviders().get(alias).getMapperTypes());
    }

    private static ProtocolMapperType type(IdentityProviderMapperTypeRepresentation type) {
        List<ProtocolMapperType.Property> properties = type.getProperties() == null ? List.of()
                : type.getProperties().stream()
                        .map(p -> new ProtocolMapperType.Property(p.getName(), p.getLabel(), p.getType(),
                                p.isRequired(), p.getDefaultValue(), p.getOptions(), p.getHelpText()))
                        .toList();
        return new ProtocolMapperType(type.getId(), type.getName(), type.getCategory(), type.getHelpText(),
                properties);
    }
}
