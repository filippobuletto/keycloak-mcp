package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.model.MoveDirection;
import io.github.filippobuletto.keycloakmcp.model.RequiredActionDetail;
import io.github.filippobuletto.keycloakmcp.model.RequiredActionSummary;
import io.github.filippobuletto.keycloakmcp.service.RequiredActionService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class RequiredActionTools {

    @Inject
    Confirmation confirmation;

    @Inject
    RequiredActionService actions;

    @Tool(description = "List the required actions of a realm in the order Keycloak asks for them at sign-in: alias, name, enabled, and defaultAction. Required actions are steps a user must finish, such as CONFIGURE_TOTP (set up OTP) or VERIFY_EMAIL. A disabled action is never asked for; a default action is given to every new user.")
    List<RequiredActionSummary> listRequiredActions(
            @ToolArg(description = "Realm name") @NotBlank String realm) {
        return actions.list(realm);
    }

    @Tool(description = "Show one required action as listRequiredActions does, with its providerId, its place in the order (1 is asked first), its stored config, and the config keys it takes (configProperties: key, label, type, default value, help text; empty when it takes no config).")
    RequiredActionDetail getRequiredAction(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Required action alias, for example CONFIGURE_TOTP") @NotBlank String alias) {
        return actions.get(realm, alias);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Enable or disable a required action, or make it a default action (given to every new user) or not. A field left out keeps its value. Disabling an enabled action stops asking users for it, and when a flow in use requires the matching step (OTP for CONFIGURE_TOTP, WebAuthn for webauthn-register) users without that credential cannot sign in; turning on a default action affects every new user. Either change "
            + Confirmation.ASKS_LOWER
            + " Returns the action as getRequiredAction does. Refused for the master realm.")
    RequiredActionDetail updateRequiredAction(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Required action alias, for example CONFIGURE_TOTP") @NotBlank String alias,
            @ToolArg(description = "false to disable the action (asks the user to confirm), true to enable it") Optional<Boolean> enabled,
            @ToolArg(description = "true to give the action to every new user (asks the user to confirm), false to stop") Optional<Boolean> defaultAction,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        return actions.update(realm, alias, enabled.orElse(null), defaultAction.orElse(null),
                consequences -> confirmation.require("updateRequiredAction", elicitation, confirm,
                        consequences + " Proceed?", consequences + " Call again with confirm=true to proceed."));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Move a required action one place UP (asked earlier at sign-in) or DOWN (asked later). Moving the first action up or the last down is refused. Returns the actions as listRequiredActions does. Refused for the master realm.")
    List<RequiredActionSummary> moveRequiredAction(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Required action alias, for example VERIFY_EMAIL") @NotBlank String alias,
            @ToolArg(description = "UP or DOWN") @NotNull MoveDirection direction) {
        return actions.move(realm, alias, direction);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Merge config into a required action's config: supplied keys change, other stored keys keep their values. Keys must be among the action's configProperties (see getRequiredAction); Keycloak checks the values. Refused for an action that takes no config. Returns the action as getRequiredAction does. Refused for the master realm.")
    RequiredActionDetail setRequiredActionConfig(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Required action alias, for example UPDATE_PASSWORD") @NotBlank String alias,
            @ToolArg(description = "Config keys to set, as string keys and values, e.g. {\"max_auth_age\": \"300\"}") @NotNull Map<String, String> config) {
        return actions.setConfig(realm, alias, config);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Remove a required action's config; the action then uses its defaults. Refused for an action that takes no config. Returns the action as getRequiredAction does. Refused for the master realm.")
    RequiredActionDetail deleteRequiredActionConfig(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Required action alias, for example UPDATE_PASSWORD") @NotBlank String alias) {
        return actions.deleteConfig(realm, alias);
    }
}
