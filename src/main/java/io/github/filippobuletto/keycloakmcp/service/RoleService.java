package io.github.filippobuletto.keycloakmcp.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;

import io.github.filippobuletto.keycloakmcp.model.RoleDetail;
import io.github.filippobuletto.keycloakmcp.model.RoleSummary;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class RoleService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientService clients;

    public List<RoleSummary> listRealmRoles(String realm) {
        return KeycloakErrors.call(() -> summaries(keycloak.realm(realm).roles().list()));
    }

    public List<RoleSummary> listClientRoles(String realm, String clientId) {
        String id = clients.find(realm, clientId).getId();
        return KeycloakErrors.call(() -> summaries(keycloak.realm(realm).clients().get(id).roles().list()));
    }

    /** {@code clientId} null means a realm role. Users and groups that hold the role directly, one page of each. */
    public RoleDetail get(String realm, String clientId, String name, int first, int max) {
        RolesResource roles = clientId == null ? keycloak.realm(realm).roles()
                : keycloak.realm(realm).clients().get(clients.find(realm, clientId).getId()).roles();
        return KeycloakErrors.call(() -> {
            // search first, so an unknown role gets a message while an unknown realm keeps Keycloak's 404
            if (roles.list(name, true).stream().noneMatch(role -> role.getName().equals(name))) {
                throw new ToolCallException("Role " + name + (clientId == null ? "" : " of client " + clientId)
                        + " not found in realm " + realm);
            }
            RoleResource role = roles.get(name);
            RoleRepresentation representation = role.toRepresentation();
            Map<String, String> clientIds = new HashMap<>();
            List<RoleDetail.Composite> composites = role.getRoleComposites().stream()
                    .map(c -> new RoleDetail.Composite(c.getName(), Boolean.TRUE.equals(c.getClientRole())
                            ? clientIds.computeIfAbsent(c.getContainerId(),
                                    id -> keycloak.realm(realm).clients().get(id).toRepresentation().getClientId())
                            : null))
                    .sorted(Comparator.comparing((RoleDetail.Composite c) -> c.clientId() == null ? "" : c.clientId())
                            .thenComparing(RoleDetail.Composite::name))
                    .toList();
            return new RoleDetail(
                    representation.getId(),
                    representation.getName(),
                    representation.getDescription(),
                    Boolean.TRUE.equals(representation.isComposite()),
                    Boolean.TRUE.equals(representation.getClientRole()),
                    clientId,
                    composites,
                    role.getUserMembers(true, first, max).stream().map(UserService::summary).toList(),
                    role.getRoleGroupMembers(first, max).stream().map(GroupService::summary).toList());
        });
    }

    public RoleSummary createRealmRole(String realm, String name, String description) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        role.setDescription(description);
        return KeycloakErrors.call(() -> {
            keycloak.realm(realm).roles().create(role);
            return summary(keycloak.realm(realm).roles().get(name).toRepresentation());
        });
    }

    public void assignRealmRole(String realm, String userId, String role) {
        KeycloakErrors.run(() -> keycloak.realm(realm).users().get(userId).roles().realmLevel()
                .add(List.of(realmRole(realm, role))));
    }

    public void unassignRealmRole(String realm, String userId, String role) {
        KeycloakErrors.run(() -> keycloak.realm(realm).users().get(userId).roles().realmLevel()
                .remove(List.of(realmRole(realm, role))));
    }

    public void deleteRealmRole(String realm, String name) {
        KeycloakErrors.run(() -> keycloak.realm(realm).roles().deleteRole(name));
    }

    private RoleRepresentation realmRole(String realm, String name) {
        return keycloak.realm(realm).roles().get(name).toRepresentation();
    }

    private static List<RoleSummary> summaries(List<RoleRepresentation> roles) {
        return roles.stream().map(RoleService::summary).toList();
    }

    static RoleSummary summary(RoleRepresentation role) {
        return new RoleSummary(role.getId(), role.getName(), role.getDescription(), Boolean.TRUE.equals(role.isComposite()),
                Boolean.TRUE.equals(role.getClientRole()));
    }
}
