package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.GroupSummary;
import io.github.filippobuletto.keycloakmcp.model.UserDetail;
import io.github.filippobuletto.keycloakmcp.model.UserInput;
import io.github.filippobuletto.keycloakmcp.model.RoleMappings;
import io.github.filippobuletto.keycloakmcp.model.UserSummary;
import io.github.filippobuletto.keycloakmcp.service.UserService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class UserTools {

    @Inject
    UserService users;

    @Inject
    Confirmation confirmation;

    @Tool(description = "Search users in a realm. Returns at most `max` users starting at `first`. An empty search lists users.")
    List<UserSummary> searchUsers(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Substring matched against username, email, first and last name. Use `j*` for prefix or `\"jdoe\"` for exact match", defaultValue = "") String search,
            @ToolArg(description = "Number of results to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max results (1-500)", defaultValue = "50") @Min(1) @Max(500) int max) {
        return users.search(realm, search, first, max);
    }

    @Tool(description = "Get a user's details, including attributes, enabled flag and required actions.")
    UserDetail getUser(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId) {
        return users.get(realm, userId);
    }

    @Tool(description = "List the groups a user belongs to directly, each with its full path such as /finance/payables.")
    List<GroupSummary> getUserGroups(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId) {
        return users.groups(realm, userId);
    }

    @Tool(description = "List the realm roles and client roles mapped directly to a user. Client roles are keyed by clientId.")
    RoleMappings getUserRoles(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId) {
        return users.roles(realm, userId);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create a user. The user is enabled unless enabled=false. Returns the created user. Refused for the master realm.")
    UserDetail createUser(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Username, unique in the realm") @NotBlank String username,
            @ToolArg(description = "Email address") Optional<@Email String> email,
            @ToolArg(description = "First name") Optional<String> firstName,
            @ToolArg(description = "Last name") Optional<String> lastName,
            @ToolArg(description = "Whether the user can log in; default true") Optional<Boolean> enabled,
            @ToolArg(description = "Custom attributes, each a list of values, e.g. {\"department\": [\"finance\"]}") Optional<Map<String, List<String>>> attributes) {
        return users.create(realm, username, input(email, firstName, lastName, enabled, attributes));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update a user. Only the supplied fields change; supplied attributes replace those keys and keep the others. Refused for the master realm.")
    UserDetail updateUser(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "Email address") Optional<@Email String> email,
            @ToolArg(description = "First name") Optional<String> firstName,
            @ToolArg(description = "Last name") Optional<String> lastName,
            @ToolArg(description = "Whether the user can log in") Optional<Boolean> enabled,
            @ToolArg(description = "Attributes to set, each a list of values, e.g. {\"department\": [\"finance\"]}") Optional<Map<String, List<String>>> attributes) {
        return users.update(realm, userId, input(email, firstName, lastName, enabled, attributes));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Set a user's password. Temporary by default, so the user must change it at next login. The password is never returned. Refused for the master realm.")
    String resetPassword(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = "New password") @NotBlank String password,
            @ToolArg(description = "Whether the user must change it at next login", defaultValue = "true") boolean temporary) {
        users.resetPassword(realm, userId, password, temporary);
        return "Password set for user " + userId + " in realm " + realm + (temporary ? "; change required at next login" : "");
    }

    private static UserInput input(Optional<String> email, Optional<String> firstName, Optional<String> lastName,
            Optional<Boolean> enabled, Optional<Map<String, List<String>>> attributes) {
        return new UserInput(email.orElse(null), firstName.orElse(null), lastName.orElse(null), enabled.orElse(null),
                attributes.orElse(null));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a user. Irreversible. " + Confirmation.ASKS + " Refused for the master realm.")
    String deleteUser(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteUser", elicitation, confirm,
                "Delete user " + userId + " in realm " + realm + "? Irreversible.");
        users.delete(realm, userId);
        return "Deleted user " + userId + " in realm " + realm;
    }
}
