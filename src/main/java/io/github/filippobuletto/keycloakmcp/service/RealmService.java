package io.github.filippobuletto.keycloakmcp.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig.UnmanagedAttributePolicy;

import io.github.filippobuletto.keycloakmcp.model.RealmDetail;
import io.github.filippobuletto.keycloakmcp.model.RealmSummary;
import io.github.filippobuletto.keycloakmcp.model.ServerVersion;

@Singleton
public class RealmService {

    @Inject
    @Caller
    Keycloak keycloak;

    public List<RealmSummary> list() {
        return KeycloakErrors.call(() -> keycloak.realms().findAll().stream().map(RealmService::summary).toList());
    }

    public RealmDetail get(String realm) {
        return KeycloakErrors.call(() -> read(realm));
    }

    /** Like {@link #get}, but an unknown realm gives an empty result instead of Keycloak's 404. */
    public Optional<RealmDetail> find(String realm) {
        return KeycloakErrors.call(() -> {
            try {
                return Optional.of(read(realm));
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() == 404) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    public ServerVersion version() {
        return KeycloakErrors.call(() -> new ServerVersion(keycloak.serverInfo().getInfo().getSystemInfo().getVersion()));
    }

    /** Realm names starting with {@code prefix}, sorted. */
    public List<String> namesStartingWith(String prefix) {
        return list().stream().map(RealmSummary::name).filter(name -> name.startsWith(prefix)).sorted().toList();
    }

    public RealmDetail create(String realm, String displayName, boolean enabled) {
        RealmRepresentation representation = new RealmRepresentation();
        representation.setRealm(realm);
        representation.setDisplayName(displayName);
        representation.setEnabled(enabled);
        KeycloakErrors.run(() -> keycloak.realms().create(representation));
        return get(realm);
    }

    public void delete(String realm) {
        KeycloakErrors.run(() -> keycloak.realm(realm).remove());
    }

    private RealmDetail read(String realm) {
        RealmResource resource = keycloak.realm(realm);
        RealmRepresentation representation = resource.toRepresentation();
        UnmanagedAttributePolicy policy = resource.users().userProfile().getConfiguration().getUnmanagedAttributePolicy();
        return detail(representation, policy == null ? null : policy.name());
    }

    static RealmSummary summary(RealmRepresentation realm) {
        return new RealmSummary(realm.getId(), realm.getRealm(), realm.getDisplayName(), isTrue(realm.isEnabled()));
    }

    /** {@code unmanagedAttributePolicy} comes from the realm's user profile, which the realm representation lacks. */
    static RealmDetail detail(RealmRepresentation realm, String unmanagedAttributePolicy) {
        return new RealmDetail(
                realm.getId(),
                realm.getRealm(),
                realm.getDisplayName(),
                isTrue(realm.isEnabled()),
                new RealmDetail.Login(
                        isTrue(realm.isRegistrationAllowed()),
                        isTrue(realm.isRegistrationEmailAsUsername()),
                        isTrue(realm.isResetPasswordAllowed()),
                        isTrue(realm.isRememberMe()),
                        isTrue(realm.isVerifyEmail()),
                        isTrue(realm.isLoginWithEmailAllowed()),
                        isTrue(realm.isDuplicateEmailsAllowed()),
                        realm.getSslRequired()),
                new RealmDetail.Tokens(
                        realm.getAccessTokenLifespan(),
                        realm.getAccessCodeLifespan(),
                        realm.getSsoSessionIdleTimeout(),
                        realm.getSsoSessionMaxLifespan(),
                        realm.getOfflineSessionIdleTimeout(),
                        isTrue(realm.getRevokeRefreshToken()),
                        realm.getRefreshTokenMaxReuse(),
                        realm.getClientSessionIdleTimeout(),
                        realm.getClientSessionMaxLifespan(),
                        isTrue(realm.getOfflineSessionMaxLifespanEnabled()),
                        realm.getOfflineSessionMaxLifespan()),
                realm.getPasswordPolicy(),
                new RealmDetail.BruteForce(
                        isTrue(realm.isBruteForceProtected()),
                        realm.getBruteForceStrategy() == null ? null : realm.getBruteForceStrategy().name(),
                        isTrue(realm.isPermanentLockout()),
                        realm.getMaxTemporaryLockouts(),
                        realm.getFailureFactor(),
                        realm.getWaitIncrementSeconds(),
                        realm.getMaxFailureWaitSeconds(),
                        realm.getMaxDeltaTimeSeconds(),
                        realm.getMinimumQuickLoginWaitSeconds(),
                        realm.getQuickLoginCheckMilliSeconds()),
                adminEventsExpiration(realm.getAttributes()),
                unmanagedAttributePolicy);
    }

    /** Keycloak keeps the admin event expiry (seconds) as a realm attribute, absent when no expiry is set. */
    private static Long adminEventsExpiration(Map<String, String> attributes) {
        String value = attributes == null ? null : attributes.get("adminEventsExpiration");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
