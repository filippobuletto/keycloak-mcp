package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.ClientClientScopes;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeDetail;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeInput;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeSummary;
import io.github.filippobuletto.keycloakmcp.service.ClientScopeService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class ClientScopeTools {

    @Inject
    Confirmation confirmation;

    @Inject
    ClientScopeService scopes;

    @Tool(description = "List the client scopes of a realm, OpenID Connect and SAML, with realmDefault telling whether new clients get each one as default, optional or not at all (none).")
    List<ClientScopeSummary> listClientScopes(@ToolArg(description = "Realm name") @NotBlank String realm) {
        return scopes.list(realm);
    }

    @Tool(description = "Get a client scope by name: its settings, protocol mapper names and types, and the realm and client roles mapped to it (client roles keyed by clientId).")
    ClientScopeDetail getClientScope(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name, for example profile") @NotBlank String name) {
        return scopes.get(realm, name);
    }

    @Tool(description = "List the default and optional client scopes of a client, by scope name. Default scopes are always in its tokens; optional ones only when the client requests them with the scope parameter.")
    ClientClientScopes getClientClientScopes(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId) {
        return scopes.forClient(realm, clientId);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create an OpenID Connect client scope. Clients request it by name, so pick the name carefully: it cannot be changed later. Refused for the master realm.")
    ClientScopeDetail createClientScope(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Scope name, unique in the realm, for example billing:read") @NotBlank String name,
            @ToolArg(description = "Description shown in the admin console") Optional<String> description,
            @ToolArg(description = "true (default) to list the scope in the token's scope claim") Optional<Boolean> includeInTokenScope,
            @ToolArg(description = "true (default) to show the scope on the consent screen of clients that require consent") Optional<Boolean> displayOnConsentScreen,
            @ToolArg(description = "Text shown on the consent screen, for example Read your invoices") Optional<String> consentScreenText) {
        return scopes.create(realm, name, input(description, includeInTokenScope, displayOnConsentScreen, consentScreenText));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update an OpenID Connect client scope by name. Only the supplied fields change; the name cannot. Refused for the master realm and for SAML scopes.")
    ClientScopeDetail updateClientScope(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = "Description shown in the admin console") Optional<String> description,
            @ToolArg(description = "true to list the scope in the token's scope claim") Optional<Boolean> includeInTokenScope,
            @ToolArg(description = "true to show the scope on the consent screen of clients that require consent") Optional<Boolean> displayOnConsentScreen,
            @ToolArg(description = "Text shown on the consent screen") Optional<String> consentScreenText) {
        return scopes.update(realm, name, input(description, includeInTokenScope, displayOnConsentScreen, consentScreenText));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete an OpenID Connect client scope by name. Irreversible: every client that had it loses it, and its claims leave their tokens. "
            + Confirmation.ASKS + " Refused for the master realm, for SAML scopes and for Keycloak's built-in scopes.")
    String deleteClientScope(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteClientScope", elicitation, confirm,
                "Delete client scope " + name + " in realm " + realm + "? Every client that has it loses it. Irreversible.");
        scopes.delete(realm, name);
        return "Deleted client scope " + name + " in realm " + realm;
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add an OpenID Connect client scope to a client as default (always in its tokens) or optional (only when the client requests it with the scope parameter). A scope the client already has as the other type is moved. Refused for the master realm and for SAML scopes.")
    ClientClientScopes addClientScopeToClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = "default or optional") @Pattern(regexp = "default|optional", message = "must be default or optional") String type) {
        return scopes.addToClient(realm, clientId, name, type);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Remove a client scope from a client, whether it had it as default or optional; succeeds without change if the client does not have it. Refused for the master realm and for SAML scopes.")
    ClientClientScopes removeClientScopeFromClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = "Client scope name") @NotBlank String name) {
        return scopes.removeFromClient(realm, clientId, name);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Set whether every client created from now on in the realm gets an OpenID Connect client scope as default, as optional, or not at all (none). Existing clients keep their scopes. Realm-wide change. "
            + Confirmation.ASKS + " Refused for the master realm and for SAML scopes.")
    ClientScopeSummary setRealmDefaultClientScope(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = "default, optional or none") @Pattern(regexp = "default|optional|none", message = "must be default, optional or none") String type,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("setRealmDefaultClientScope", elicitation, confirm,
                "Set client scope " + name + " to " + type + " for every new client in realm " + realm
                        + "? Realm-wide change.");
        return scopes.setRealmDefault(realm, name, type);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Map a role to an OpenID Connect client scope: a realm role, or a client role when clientId is given. A client that uses the scope can then carry that role in its tokens. Refused for the master realm and for SAML scopes.")
    ClientScopeDetail addClientScopeRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = "Role name") @NotBlank String role,
            @ToolArg(description = "Client id that owns the role, for a client role; omit for a realm role") Optional<String> clientId) {
        return scopes.addRole(realm, name, role, clientId.orElse(null));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Remove a role mapping from an OpenID Connect client scope: a realm role, or a client role when clientId is given. Refused for the master realm and for SAML scopes.")
    ClientScopeDetail removeClientScopeRole(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String name,
            @ToolArg(description = "Role name") @NotBlank String role,
            @ToolArg(description = "Client id that owns the role, for a client role; omit for a realm role") Optional<String> clientId) {
        return scopes.removeRole(realm, name, role, clientId.orElse(null));
    }

    private static ClientScopeInput input(Optional<String> description, Optional<Boolean> includeInTokenScope,
            Optional<Boolean> displayOnConsentScreen, Optional<String> consentScreenText) {
        return new ClientScopeInput(description.orElse(null), includeInTokenScope.orElse(null),
                displayOnConsentScreen.orElse(null), consentScreenText.orElse(null));
    }
}
