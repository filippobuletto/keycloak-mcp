package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/**
 * {@code config} is always masked: a {@code clientSecret} value reads {@code **********}. When the provider sets no
 * first broker login flow, {@code firstBrokerLoginFlowAlias} is the realm's, which Keycloak runs then, and
 * {@code firstBrokerLoginFlowFromRealm} is true. {@code mappers} are sorted by name.
 */
public record IdentityProviderDetail(
        String alias,
        String displayName,
        String providerId,
        boolean enabled,
        boolean trustEmail,
        boolean storeToken,
        boolean linkOnly,
        boolean hideOnLogin,
        String firstBrokerLoginFlowAlias,
        boolean firstBrokerLoginFlowFromRealm,
        String postBrokerLoginFlowAlias,
        Map<String, String> config,
        List<Mapper> mappers) {

    /**
     * {@code syncMode} is the stored value, {@code LEGACY} when none is stored; {@code config} leaves it out.
     * {@code syncMode} is a string so that a value outside {@link SyncMode}, set outside this server, shows as stored.
     */
    public record Mapper(String name, String type, String syncMode, Map<String, String> config) {
    }
}
