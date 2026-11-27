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
import io.github.filippobuletto.keycloakmcp.model.GroupDetail;
import io.github.filippobuletto.keycloakmcp.model.GroupSummary;
import io.github.filippobuletto.keycloakmcp.model.UserSummary;
import io.github.filippobuletto.keycloakmcp.service.GroupService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class GroupTools {

    @Inject
    Confirmation confirmation;

    @Inject
    GroupService groups;

    @Tool(description = "List every group in a realm, subgroups included, each with its full path such as /finance/payables.")
    List<GroupSummary> listGroups(@ToolArg(description = "Realm name") @NotBlank String realm) {
        return groups.list(realm);
    }

    @Tool(description = "Get a group by path: attributes, subgroup count and the realm and client roles mapped to it.")
    GroupDetail getGroup(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Group path, for example /finance/payables") @NotBlank String path) {
        return groups.get(realm, path);
    }

    @Tool(description = "List the users who are direct members of a group. Returns at most `max` users starting at `first`.")
    List<UserSummary> getGroupMembers(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Group path, for example /finance/payables") @NotBlank String path,
            @ToolArg(description = "Number of results to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max results (1-500)", defaultValue = "50") @Min(1) @Max(500) int max) {
        return groups.members(realm, path, first, max);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create a group, top-level or under an existing parent. Returns the new group with its path. Refused for the master realm.")
    GroupDetail createGroup(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Group name, unique among its siblings") @NotBlank String name,
            @ToolArg(description = "Path of the parent group, for example /finance; omit for a top-level group") Optional<String> parentPath) {
        return groups.create(realm, name, parentPath.orElse(null));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add a user to a group. Refused for the master realm.")
    String addUserToGroup(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "Group path, for example /finance") @NotBlank String path) {
        groups.addMember(realm, userId, path);
        return "Added user " + userId + " to group " + path + " in realm " + realm;
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Remove a user from a group. Refused for the master realm.")
    String removeUserFromGroup(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "Group path, for example /finance") @NotBlank String path) {
        groups.removeMember(realm, userId, path);
        return "Removed user " + userId + " from group " + path + " in realm " + realm;
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a group and all its subgroups; members lose the roles it granted. Irreversible. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteGroup(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Group path, for example /finance/payables") @NotBlank String path,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteGroup", elicitation, confirm,
                "Delete group " + path + " and all its subgroups in realm " + realm + "? Irreversible.");
        groups.delete(realm, path);
        return "Deleted group " + path + " in realm " + realm;
    }
}
