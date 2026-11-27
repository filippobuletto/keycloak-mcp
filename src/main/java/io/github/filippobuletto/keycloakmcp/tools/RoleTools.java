package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.RoleDetail;
import io.github.filippobuletto.keycloakmcp.model.RoleSummary;
import io.github.filippobuletto.keycloakmcp.service.RoleService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class RoleTools {

    @Inject
    Confirmation confirmation;

    @Inject
    RoleService roles;

    @Tool(description = "List the realm roles of a realm, including built-in ones such as offline_access.")
    List<RoleSummary> listRealmRoles(@ToolArg(description = "Realm name") @NotBlank String realm) {
        return roles.listRealmRoles(realm);
    }

    @Tool(description = "List the roles defined by one client.")
    List<RoleSummary> listClientRoles(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId) {
        return roles.listClientRoles(realm, clientId);
    }

    @Tool(description = "Get a realm role, or a client role when clientId is given: the roles it contains if composite"
            + " (client roles with their clientId), and the users and groups that hold it directly, at most `max` of each"
            + " starting at `first`. Holders through groups or composite roles are not listed.")
    RoleDetail getRealmRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Role name") @NotBlank String name,
            @ToolArg(description = "Client id as shown in the admin console, for a client role; omit for a realm role") Optional<String> clientId,
            @ToolArg(description = "Number of users and groups to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max users and max groups (1-500)", defaultValue = "50") @Min(1) @Max(500) int max) {
        return roles.get(realm, clientId.filter(id -> !id.isBlank()).orElse(null), name, first, max);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create a realm role. Refused for the master realm.")
    RoleSummary createRealmRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Role name, unique in the realm") @NotBlank String name,
            @ToolArg(description = "What the role grants") Optional<String> description) {
        return roles.createRealmRole(realm, name, description.orElse(null));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Grant a realm role to a user. Refused for the master realm.")
    String assignRealmRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "Realm role name") @NotBlank String role) {
        roles.assignRealmRole(realm, userId, role);
        return "Assigned realm role " + role + " to user " + userId + " in realm " + realm;
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Remove a realm role from a user. Roles inherited through groups or composites are not affected. Refused for the master realm.")
    String unassignRealmRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "Realm role name") @NotBlank String role) {
        roles.unassignRealmRole(realm, userId, role);
        return "Removed realm role " + role + " from user " + userId + " in realm " + realm;
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a realm role; every user and group loses it. Irreversible. " + Confirmation.ASKS
            + " Refused for the master realm.")
    String deleteRealmRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Realm role name") @NotBlank String name,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteRealmRole", elicitation, confirm,
                "Delete realm role " + name + " in realm " + realm + "? Every user and group loses it. Irreversible.");
        roles.deleteRealmRole(realm, name);
        return "Deleted realm role " + name + " in realm " + realm;
    }
}
