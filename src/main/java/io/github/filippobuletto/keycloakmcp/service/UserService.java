package io.github.filippobuletto.keycloakmcp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.github.filippobuletto.keycloakmcp.model.GroupSummary;
import io.github.filippobuletto.keycloakmcp.model.UserDetail;
import io.github.filippobuletto.keycloakmcp.model.UserInput;
import io.github.filippobuletto.keycloakmcp.model.RoleMappings;
import io.github.filippobuletto.keycloakmcp.model.UserSummary;

@Singleton
public class UserService {

    @Inject
    @Caller
    Keycloak keycloak;

    public List<UserSummary> search(String realm, String search, int first, int max) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).users().search(query(search), first, max, true).stream()
                .map(UserService::summary)
                .toList());
    }

    public UserDetail get(String realm, String userId) {
        return KeycloakErrors.call(() -> detail(user(realm, userId).toRepresentation()));
    }

    public List<GroupSummary> groups(String realm, String userId) {
        return KeycloakErrors.call(() -> user(realm, userId).groups().stream()
                .map(GroupService::summary)
                .toList());
    }

    public RoleMappings roles(String realm, String userId) {
        return KeycloakErrors.call(() -> roles(user(realm, userId).roles().getAll()));
    }

    public void delete(String realm, String userId) {
        KeycloakErrors.checked(() -> keycloak.realm(realm).users().delete(userId));
    }

    public UserDetail create(String realm, String username, UserInput input) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        apply(user, input);
        String id = KeycloakErrors.createdId(() -> keycloak.realm(realm).users().create(user));
        return get(realm, id);
    }

    public UserDetail update(String realm, String userId, UserInput input) {
        KeycloakErrors.run(() -> {
            UserResource resource = user(realm, userId);
            UserRepresentation user = resource.toRepresentation();
            apply(user, input);
            resource.update(user);
        });
        return get(realm, userId);
    }

    public void resetPassword(String realm, String userId, String password, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);
        KeycloakErrors.run(() -> user(realm, userId).resetPassword(credential));
    }

    static void apply(UserRepresentation user, UserInput input) {
        if (input.email() != null) {
            user.setEmail(input.email());
        }
        if (input.firstName() != null) {
            user.setFirstName(input.firstName());
        }
        if (input.lastName() != null) {
            user.setLastName(input.lastName());
        }
        if (input.enabled() != null) {
            user.setEnabled(input.enabled());
        }
        if (input.attributes() != null) {
            Map<String, List<String>> attributes = new HashMap<>();
            if (user.getAttributes() != null) {
                attributes.putAll(user.getAttributes());
            }
            attributes.putAll(input.attributes());
            user.setAttributes(attributes);
        }
    }

    private UserResource user(String realm, String userId) {
        return keycloak.realm(realm).users().get(userId);
    }

    /**
     * Keycloak's {@code search} is prefix-based; plain terms are wrapped for substring matching. Terms with {@code *}
     * or quotes pass through so callers keep Keycloak's prefix and exact syntax.
     */
    static String query(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = search.strip();
        return term.contains("*") || term.startsWith("\"") ? term : "*" + term + "*";
    }

    static UserSummary summary(UserRepresentation user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail(), user.getFirstName(),
                user.getLastName(), Boolean.TRUE.equals(user.isEnabled()));
    }

    static UserDetail detail(UserRepresentation user) {
        return new UserDetail(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                Boolean.TRUE.equals(user.isEnabled()),
                Boolean.TRUE.equals(user.isEmailVerified()),
                user.getCreatedTimestamp(),
                user.getAttributes() == null ? Map.of() : user.getAttributes(),
                user.getRequiredActions() == null ? List.of() : user.getRequiredActions());
    }

    static RoleMappings roles(MappingsRepresentation mappings) {
        List<String> realmRoles = names(mappings.getRealmMappings());
        Map<String, List<String>> clientRoles = new TreeMap<>();
        if (mappings.getClientMappings() != null) {
            for (ClientMappingsRepresentation client : mappings.getClientMappings().values()) {
                clientRoles.put(client.getClient(), names(client.getMappings()));
            }
        }
        return new RoleMappings(realmRoles, clientRoles);
    }

    private static List<String> names(List<RoleRepresentation> roles) {
        return roles == null ? List.of() : roles.stream().map(RoleRepresentation::getName).sorted().toList();
    }
}
