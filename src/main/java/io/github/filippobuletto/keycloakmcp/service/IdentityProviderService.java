package io.github.filippobuletto.keycloakmcp.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

import io.github.filippobuletto.keycloakmcp.model.IdentityProviderDetail;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderInput;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderSummary;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderType;
import io.github.filippobuletto.keycloakmcp.model.SyncMode;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class IdentityProviderService {

    static final String CLIENT_SECRET = "clientSecret";

    @Inject
    @Caller
    Keycloak keycloak;

    public List<IdentityProviderType> types() {
        ServerInfoRepresentation info = KeycloakErrors.call(() -> keycloak.serverInfo().getInfo());
        Set<String> social = ids(info.getSocialProviders());
        return (info.getIdentityProviders() == null ? List.<Map<String, String>> of() : info.getIdentityProviders())
                .stream()
                .map(type -> new IdentityProviderType(type.get("id"), type.get("name"), social.contains(type.get("id"))))
                .toList();
    }

    private static Set<String> ids(List<Map<String, String>> types) {
        return types == null ? Set.of() : types.stream().map(type -> type.get("id")).collect(Collectors.toSet());
    }

    public List<IdentityProviderSummary> list(String realm) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).identityProviders().findAll()).stream()
                .map(p -> new IdentityProviderSummary(p.getAlias(), p.getDisplayName(), p.getProviderId(),
                        p.isEnabled()))
                .toList();
    }

    public IdentityProviderDetail get(String realm, String alias) {
        IdentityProviderRepresentation provider = find(realm, alias);
        // Keycloak runs the realm's first broker login flow for a provider that sets none
        String realmFlow = provider.getFirstBrokerLoginFlowAlias() == null
                ? KeycloakErrors.call(() -> keycloak.realm(realm).toRepresentation().getFirstBrokerLoginFlow())
                : null;
        List<IdentityProviderMapperRepresentation> mappers = KeycloakErrors
                .call(() -> keycloak.realm(realm).identityProviders().get(alias).getMappers());
        return detail(provider, realmFlow, mappers);
    }

    public IdentityProviderDetail create(String realm, String alias, String providerId, Map<String, String> config,
            IdentityProviderInput input) {
        checkType(providerId);
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias(alias);
        provider.setProviderId(providerId);
        provider.setConfig(new HashMap<>(config));
        apply(provider, input);
        KeycloakErrors.checked(() -> keycloak.realm(realm).identityProviders().create(provider));
        return get(realm, alias);
    }

    /**
     * Merges the supplied fields and config keys into the provider. Without a supplied {@code clientSecret} the
     * merged config carries back Keycloak's mask, and Keycloak then keeps the stored secret.
     *
     * @param confirmDisable runs only when the call disables an enabled provider; it throws to refuse
     */
    public IdentityProviderDetail update(String realm, String alias, Map<String, String> config,
            IdentityProviderInput input, Runnable confirmDisable) {
        IdentityProviderRepresentation provider = find(realm, alias);
        if (provider.isEnabled() && Boolean.FALSE.equals(input.enabled())) {
            confirmDisable.run();
        }
        Map<String, String> merged = provider.getConfig() == null ? new HashMap<>()
                : new HashMap<>(provider.getConfig());
        merged.putAll(config);
        provider.setConfig(merged);
        apply(provider, input);
        KeycloakErrors.run(() -> keycloak.realm(realm).identityProviders().get(alias).update(provider));
        return get(realm, alias);
    }

    public void delete(String realm, String alias) {
        find(realm, alias);
        KeycloakErrors.run(() -> keycloak.realm(realm).identityProviders().get(alias).remove());
    }

    private void checkType(String providerId) {
        List<String> ids = types().stream().map(IdentityProviderType::id).sorted().toList();
        if (!ids.contains(providerId)) {
            throw new ToolCallException("Identity provider type " + providerId + " is not a type of this server;"
                    + " valid types: " + String.join(", ", ids) + ". Nothing was changed.");
        }
    }

    static void apply(IdentityProviderRepresentation provider, IdentityProviderInput input) {
        if (input.displayName() != null) {
            provider.setDisplayName(input.displayName());
        }
        if (input.enabled() != null) {
            provider.setEnabled(input.enabled());
        }
        if (input.trustEmail() != null) {
            provider.setTrustEmail(input.trustEmail());
        }
        if (input.storeToken() != null) {
            provider.setStoreToken(input.storeToken());
        }
        if (input.linkOnly() != null) {
            provider.setLinkOnly(input.linkOnly());
        }
        if (input.hideOnLogin() != null) {
            provider.setHideOnLogin(input.hideOnLogin());
        }
        if (input.firstBrokerLoginFlowAlias() != null) {
            provider.setFirstBrokerLoginFlowAlias(input.firstBrokerLoginFlowAlias());
        }
        if (input.postBrokerLoginFlowAlias() != null) {
            provider.setPostBrokerLoginFlowAlias(input.postBrokerLoginFlowAlias());
        }
    }

    IdentityProviderRepresentation find(String realm, String alias) {
        return KeycloakErrors.call(() -> {
            try {
                return keycloak.realm(realm).identityProviders().get(alias).toRepresentation();
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 404) {
                    throw e;
                }
                throw new ToolCallException("Identity provider " + alias + " not found in realm " + realm);
            }
        });
    }

    private static IdentityProviderDetail detail(IdentityProviderRepresentation p, String realmFlow,
            List<IdentityProviderMapperRepresentation> mappers) {
        boolean fromRealm = p.getFirstBrokerLoginFlowAlias() == null;
        return new IdentityProviderDetail(p.getAlias(), p.getDisplayName(), p.getProviderId(), p.isEnabled(),
                Boolean.TRUE.equals(p.isTrustEmail()), Boolean.TRUE.equals(p.isStoreToken()),
                Boolean.TRUE.equals(p.isLinkOnly()), Boolean.TRUE.equals(p.isHideOnLogin()),
                fromRealm ? realmFlow : p.getFirstBrokerLoginFlowAlias(), fromRealm, p.getPostBrokerLoginFlowAlias(),
                mask(p.getConfig()),
                (mappers == null ? List.<IdentityProviderMapperRepresentation> of() : mappers).stream()
                        .map(IdentityProviderService::mapper)
                        .sorted(Comparator.comparing(IdentityProviderDetail.Mapper::name))
                        .toList());
    }

    private static IdentityProviderDetail.Mapper mapper(IdentityProviderMapperRepresentation m) {
        Map<String, String> config = m.getConfig() == null ? new TreeMap<>() : new TreeMap<>(m.getConfig());
        // Keycloak runs a mapper without a sync mode as LEGACY (IdentityProviderMapperModel.getSyncMode)
        String syncMode = Objects.requireNonNullElse(config.remove(SyncMode.CONFIG_KEY), SyncMode.LEGACY.name());
        return new IdentityProviderDetail.Mapper(m.getName(), m.getIdentityProviderMapper(), syncMode, config);
    }

    /** A sorted copy of {@code config} whose {@code clientSecret}, if any, reads as Keycloak's secret mask. */
    static Map<String, String> mask(Map<String, String> config) {
        Map<String, String> masked = config == null ? new TreeMap<>() : new TreeMap<>(config);
        masked.computeIfPresent(CLIENT_SECRET, (key, value) -> ComponentRepresentation.SECRET_VALUE);
        return masked;
    }
}
