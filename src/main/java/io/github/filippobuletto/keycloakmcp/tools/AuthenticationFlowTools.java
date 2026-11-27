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
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.AuthenticationFlowDetail;
import io.github.filippobuletto.keycloakmcp.model.AuthenticationFlowSummary;
import io.github.filippobuletto.keycloakmcp.model.AuthenticatorType;
import io.github.filippobuletto.keycloakmcp.model.ClientFlowBinding;
import io.github.filippobuletto.keycloakmcp.model.FlowType;
import io.github.filippobuletto.keycloakmcp.model.MoveDirection;
import io.github.filippobuletto.keycloakmcp.model.RealmFlowSlot;
import io.github.filippobuletto.keycloakmcp.model.Requirement;
import io.github.filippobuletto.keycloakmcp.model.SubFlowType;
import io.github.filippobuletto.keycloakmcp.service.AuthenticationFlowService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class AuthenticationFlowTools {

    @Inject
    Confirmation confirmation;

    @Inject
    AuthenticationFlowService flows;

    @Tool(description = "List the top-level authentication flows of a realm: alias, description, type (basic-flow or client-flow), whether it is built in, and what uses it (usedBy): realm bindings such as 'realm: BROWSER', client overrides such as 'client kiosk: BROWSER', identity providers such as 'identity provider corp: FIRST_BROKER_LOGIN'. A flow with an empty usedBy affects no sign-in.")
    List<AuthenticationFlowSummary> listAuthenticationFlows(
            @ToolArg(description = "Realm name") @NotBlank String realm) {
        return flows.list(realm);
    }

    @Tool(description = "Show a top-level authentication flow as listAuthenticationFlows does, with its executions in the order Keycloak runs them: id (used by the execution tools), level (nesting depth, 0 for the flow's own steps), display name, providerId for an authenticator step or subFlowAlias for a sub-flow, requirement, the requirements it allows, whether it takes config, and its config alias and map if it has one.")
    AuthenticationFlowDetail getAuthenticationFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of a top-level flow, for example browser") @NotBlank String alias) {
        return flows.get(realm, alias);
    }

    @Tool(description = "List the authenticator types of the Keycloak server that flow steps can use: id, name, description and kind (authenticator, form, form-action or client-authenticator). With providerId, show only that type with its config properties (key, label, type, default value, options, help text).")
    List<AuthenticatorType> listAuthenticatorTypes(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Type id whose config properties to show, for example identity-provider-redirector") Optional<String> providerId) {
        return flows.types(realm, providerId.orElse(null));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create an empty top-level authentication flow. It affects no sign-in until it is bound. To change a built-in flow, use copyAuthenticationFlow instead. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail createAuthenticationFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the new flow, unique in the realm") @NotBlank String alias,
            @ToolArg(description = "Description") Optional<String> description,
            @ToolArg(description = "BASIC for a flow that signs users in, CLIENT for one that authenticates clients") @NotNull FlowType type) {
        return flows.create(realm, alias, description.orElse(null), type);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Copy an authentication flow with all its steps, sub-flows and configs under a new alias. This is how to change a built-in flow: copy it, change the copy, then bind the copy. The copy affects no sign-in until it is bound. Returns the copy as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail copyAuthenticationFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow to copy, for example browser") @NotBlank String alias,
            @ToolArg(description = "Alias of the copy, unique in the realm") @NotBlank String newAlias) {
        return flows.copy(realm, alias, newAlias);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a top-level authentication flow with its sub-flows and configs. Built-in flows and flows in use (see usedBy in listAuthenticationFlows) are refused. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteAuthenticationFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String alias,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteAuthenticationFlow", elicitation, confirm,
                "Delete authentication flow " + alias + " in realm " + realm + " with all its steps and configs?");
        flows.delete(realm, alias);
        return "Deleted authentication flow " + alias + " in realm " + realm;
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add an authenticator step to the end of a top-level flow, or of a sub-flow inside it. Keycloak adds it as DISABLED; set its requirement with updateFlowExecution. Built-in flows are refused: copy them first. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail addFlowExecution(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Alias of a sub-flow inside the flow to add into; the flow itself when omitted") Optional<String> parent,
            @ToolArg(description = "Authenticator type id from listAuthenticatorTypes, for example auth-otp-form") @NotBlank String providerId) {
        return flows.addExecution(realm, flowAlias, parent.orElse(null), providerId);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add a sub-flow to the end of a top-level flow, or of a sub-flow inside it. Keycloak adds it as DISABLED. A FORM sub-flow takes formProviderId, a type of kind form such as registration-page-form. Built-in flows are refused: copy them first. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail addFlowSubFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Alias of a sub-flow inside the flow to add into; the flow itself when omitted") Optional<String> parent,
            @ToolArg(description = "Alias of the new sub-flow, unique in the realm") @NotBlank String alias,
            @ToolArg(description = "Description") Optional<String> description,
            @ToolArg(description = "BASIC runs authenticator steps, FORM renders one form and runs form actions") @NotNull SubFlowType type,
            @ToolArg(description = "Form type id for a FORM sub-flow, for example registration-page-form") Optional<String> formProviderId) {
        return flows.addSubFlow(realm, flowAlias, parent.orElse(null), alias, description.orElse(null), type,
                formProviderId.orElse(null));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Set the requirement of a step or sub-flow in a top-level flow. Only the requirements the execution allows (requirementChoices in getAuthenticationFlow) are accepted. Built-in flows are refused: copy them first. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail updateFlowExecution(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Execution id from getAuthenticationFlow") @NotBlank String executionId,
            @ToolArg(description = "REQUIRED, ALTERNATIVE, DISABLED or CONDITIONAL") @NotNull Requirement requirement) {
        return flows.updateExecution(realm, flowAlias, executionId, requirement);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Move a step or sub-flow one place UP (runs earlier) or DOWN (runs later) among its siblings at the same level. The first step cannot move up, nor the last down. Built-in flows are refused: copy them first. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail moveFlowExecution(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Execution id from getAuthenticationFlow") @NotBlank String executionId,
            @ToolArg(description = "UP or DOWN") @NotNull MoveDirection direction) {
        return flows.moveExecution(realm, flowAlias, executionId, direction);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Remove a step or sub-flow from a top-level flow; a sub-flow goes with all its steps and configs. Built-in flows are refused. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteFlowExecution(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Execution id from getAuthenticationFlow") @NotBlank String executionId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        String name = flows.executionName(realm, flowAlias, executionId);
        confirmation.require("deleteFlowExecution", elicitation, confirm,
                "Remove " + name + " from authentication flow " + flowAlias + " in realm " + realm
                        + "? A sub-flow goes with all its steps.");
        flows.deleteExecution(realm, flowAlias, executionId);
        return "Deleted execution " + name + " from authentication flow " + flowAlias + " in realm " + realm;
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Set the config of a step that takes config (configurable in getAuthenticationFlow). A step without config gets a new one named by alias; for a step with config, the supplied keys change and every other key keeps its value. Config keys must be ones listAuthenticatorTypes with the step's providerId shows. Built-in flows are refused: copy them first. Returns the flow as getAuthenticationFlow does. Refused for the master realm.")
    AuthenticationFlowDetail setExecutionConfig(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Execution id from getAuthenticationFlow") @NotBlank String executionId,
            @ToolArg(description = "Alias of a new config, unique in the realm; needed only when the step has no config yet") Optional<String> alias,
            @ToolArg(description = "Config as string keys and values, e.g. {\"defaultProvider\": \"corporate-oidc\"}") @NotNull Map<String, String> config) {
        return flows.setConfig(realm, flowAlias, executionId, alias.orElse(null), config);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete the config of a step; the step then runs with its defaults. Built-in flows are refused. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteExecutionConfig(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias of the top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = "Execution id from getAuthenticationFlow") @NotBlank String executionId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        String name = flows.configuredExecutionName(realm, flowAlias, executionId);
        confirmation.require("deleteExecutionConfig", elicitation, confirm,
                "Delete the config of " + name + " in authentication flow " + flowAlias + " in realm " + realm
                        + "? The step then runs with its defaults.");
        flows.deleteConfig(realm, flowAlias, executionId);
        return "Deleted the config of " + name + " in authentication flow " + flowAlias + " in realm " + realm;
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Bind a top-level flow to a realm slot, so every sign-in of that kind in the realm runs it at once: BROWSER, REGISTRATION, DIRECT_GRANT, RESET_CREDENTIALS, CLIENT_AUTHENTICATION (takes a client-flow), DOCKER_AUTHENTICATION or FIRST_BROKER_LOGIN (the others take a basic-flow). Clients with their own override keep it. Returns the alias it replaced, to bind back if needed. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String bindRealmFlow(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Realm slot to bind") @NotNull RealmFlowSlot slot,
            @ToolArg(description = "Alias of a top-level flow") @NotBlank String flowAlias,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        String previous = flows.bind(realm, slot, flowAlias,
                () -> confirmation.require("bindRealmFlow", elicitation, confirm,
                        "Bind authentication flow " + flowAlias + " to " + slot + " in realm " + realm
                                + "? Every such sign-in in the realm runs it at once."));
        return "Bound authentication flow " + flowAlias + " to " + slot + " in realm " + realm
                + (previous == null ? "" : "; it replaces " + previous);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Set a client's BROWSER or DIRECT_GRANT flow override to a top-level basic-flow, so the client's sign-ins run it instead of the realm's flow; without flowAlias, clear the override so the client uses the realm's flow again. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String setClientFlowOverride(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id, for example kiosk") @NotBlank String clientId,
            @ToolArg(description = "BROWSER or DIRECT_GRANT") @NotNull ClientFlowBinding binding,
            @ToolArg(description = "Alias of a top-level basic-flow; omit to clear the override") Optional<String> flowAlias,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        String question = flowAlias
                .map(alias -> "Set the " + binding + " flow of client " + clientId + " in realm " + realm + " to "
                        + alias + "? The client's sign-ins run it at once.")
                .orElse("Clear the " + binding + " flow override of client " + clientId + " in realm " + realm
                        + "? The client then uses the realm's flow.");
        flows.override(realm, clientId, binding, flowAlias.orElse(null),
                () -> confirmation.require("setClientFlowOverride", elicitation, confirm, question));
        return flowAlias
                .map(alias -> "Set the " + binding + " flow of client " + clientId + " in realm " + realm + " to "
                        + alias)
                .orElse("Cleared the " + binding + " flow of client " + clientId + " in realm " + realm
                        + "; it uses the realm's flow");
    }
}
