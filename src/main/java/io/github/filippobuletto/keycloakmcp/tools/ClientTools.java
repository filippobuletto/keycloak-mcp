package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.ClientDetail;
import io.github.filippobuletto.keycloakmcp.model.ClientInput;
import io.github.filippobuletto.keycloakmcp.model.ClientSecret;
import io.github.filippobuletto.keycloakmcp.model.ClientSummary;
import io.github.filippobuletto.keycloakmcp.model.ServiceAccountDetail;
import io.github.filippobuletto.keycloakmcp.service.ClientService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class ClientTools {

    @Inject
    Confirmation confirmation;

    @Inject
    ClientService clients;

    @Tool(description = "List the clients of a realm, including built-in ones such as account and admin-cli.")
    List<ClientSummary> listClients(@ToolArg(description = "Realm name") @NotBlank String realm) {
        return clients.list(realm);
    }

    @Tool(description = "Get a client's settings by clientId: flows (including implicit), full scope allowed, client authenticator, URLs, redirect URIs, web origins, selected OIDC and SAML attributes, the client's own protocol mappers and its role scope mappings. Never includes secrets, certificates or keys.")
    ClientDetail getClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console, for example billing-api (not the internal UUID)") @NotBlank String clientId) {
        return clients.get(realm, clientId);
    }

    @Tool(description = "Get a client's service account user: user id, username, the realm and client roles mapped to it"
            + " directly (client roles keyed by clientId), its effective realm roles (through groups and composite roles)"
            + " and its groups with their paths. Fails for a client without a service account.")
    ServiceAccountDetail getServiceAccount(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId) {
        return clients.serviceAccount(realm, clientId);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create an OpenID Connect client. Confidential unless publicClient=true; Keycloak generates the secret, which is never returned. Refused for the master realm.")
    ClientDetail createClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id, unique in the realm, for example billing-api") @NotBlank String clientId,
            @ToolArg(description = "Display name") Optional<String> name,
            @ToolArg(description = "true for browser or mobile apps that cannot keep a secret") Optional<Boolean> publicClient,
            @ToolArg(description = "true to let the client obtain tokens for itself (client credentials grant)") Optional<Boolean> serviceAccountsEnabled,
            @ToolArg(description = "Browser login (authorization code flow). Off by default for confidential clients with service accounts") Optional<Boolean> standardFlowEnabled,
            @ToolArg(description = "Password grant: the client may exchange a user's password for tokens. Off by default for confidential clients with service accounts") Optional<Boolean> directAccessGrantsEnabled,
            @ToolArg(description = "Allowed redirect URIs; * wildcards allowed, e.g. https://app.example/*") Optional<List<String>> redirectUris,
            @ToolArg(description = "Allowed CORS origins, e.g. https://app.example") Optional<List<String>> webOrigins) {
        return clients.create(realm, clientId, input(name, publicClient, serviceAccountsEnabled, standardFlowEnabled,
                directAccessGrantsEnabled, redirectUris, webOrigins));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update a client by clientId. Only the supplied fields change; lists replace the existing ones. Refused for the master realm.")
    ClientDetail updateClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = "Display name") Optional<String> name,
            @ToolArg(description = "true for browser or mobile apps that cannot keep a secret") Optional<Boolean> publicClient,
            @ToolArg(description = "true to let the client obtain tokens for itself (client credentials grant)") Optional<Boolean> serviceAccountsEnabled,
            @ToolArg(description = "Browser login (authorization code flow)") Optional<Boolean> standardFlowEnabled,
            @ToolArg(description = "Password grant: the client may exchange a user's password for tokens") Optional<Boolean> directAccessGrantsEnabled,
            @ToolArg(description = "Allowed redirect URIs; replaces the current list") Optional<List<String>> redirectUris,
            @ToolArg(description = "Allowed CORS origins; replaces the current list") Optional<List<String>> webOrigins) {
        return clients.update(realm, clientId, input(name, publicClient, serviceAccountsEnabled, standardFlowEnabled,
                directAccessGrantsEnabled, redirectUris, webOrigins));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a client by clientId. Irreversible: apps using it can no longer log in. " + Confirmation.ASKS
            + " Refused for the master realm.")
    String deleteClient(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteClient", elicitation, confirm,
                "Delete client " + clientId + " in realm " + realm + "? Irreversible: apps using it can no longer log in.");
        clients.delete(realm, clientId);
        return "Deleted client " + clientId + " in realm " + realm;
    }

    static final String GET_CLIENT_SECRET = "getClientSecret";

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(name = GET_CLIENT_SECRET, description = "Read a confidential client's secret. The result is a live credential: anyone who sees it can authenticate as the client. "
            + Confirmation.ASKS
            + " Refused for the master realm and for public or bearer-only clients. Available only when the server runs with MCP_CLIENT_SECRETS_ENABLED=true.")
    ClientSecret getClientSecret(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require(GET_CLIENT_SECRET, elicitation, confirm,
                "Show the secret of client " + clientId + " in realm " + realm + "? The result is a live credential.");
        return clients.secret(realm, clientId);
    }

    static final String REGENERATE_CLIENT_SECRET = "regenerateClientSecret";

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(name = REGENERATE_CLIENT_SECRET, description = "Replace a confidential client's secret with a new one generated by Keycloak and return it. The old secret stops working at once, so every app still using it fails to authenticate until it gets the new one. The result is a live credential. "
            + Confirmation.ASKS
            + " Refused for the master realm and for public or bearer-only clients. Available only when the server runs with MCP_CLIENT_SECRETS_ENABLED=true.")
    ClientSecret regenerateClientSecret(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id as shown in the admin console (not the internal UUID)") @NotBlank String clientId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require(REGENERATE_CLIENT_SECRET, elicitation, confirm,
                "Replace the secret of client " + clientId + " in realm " + realm + "? The old secret stops working at once.");
        return clients.regenerateSecret(realm, clientId);
    }

    private static ClientInput input(Optional<String> name, Optional<Boolean> publicClient,
            Optional<Boolean> serviceAccountsEnabled, Optional<Boolean> standardFlowEnabled,
            Optional<Boolean> directAccessGrantsEnabled, Optional<List<String>> redirectUris,
            Optional<List<String>> webOrigins) {
        return new ClientInput(name.orElse(null), publicClient.orElse(null), serviceAccountsEnabled.orElse(null),
                standardFlowEnabled.orElse(null), directAccessGrantsEnabled.orElse(null), redirectUris.orElse(null),
                webOrigins.orElse(null));
    }
}
