package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderDetail;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderInput;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderSummary;
import io.github.filippobuletto.keycloakmcp.model.IdentityProviderType;
import io.github.filippobuletto.keycloakmcp.service.IdentityProviderService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class IdentityProviderTools {

    @Inject
    Confirmation confirmation;

    @Inject
    IdentityProviderService providers;

    @Tool(description = "List the identity provider types of the Keycloak server: type id (for example oidc, saml, google), display name, and whether it is a social provider.")
    List<IdentityProviderType> listIdentityProviderTypes() {
        return providers.types();
    }

    @Tool(description = "List the identity providers of a realm: alias, display name, type id and whether it is enabled.")
    List<IdentityProviderSummary> listIdentityProviders(
            @ToolArg(description = "Realm name") @NotBlank String realm) {
        return providers.list(realm);
    }

    @Tool(description = "Show an identity provider by alias: display name, type id, enabled, trustEmail, storeToken, linkOnly, hideOnLogin, first and post broker login flows, its config map, and its mappers with name, type, sync mode and config. A clientSecret in the config is always masked. When the provider sets no first broker login flow, firstBrokerLoginFlowAlias shows the realm's flow, which Keycloak runs then, and firstBrokerLoginFlowFromRealm is true. A mapper without a stored sync mode shows LEGACY, which Keycloak runs then.")
    IdentityProviderDetail getIdentityProvider(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias, for example google") @NotBlank String alias) {
        return providers.get(realm, alias);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Create an identity provider in a realm, so users can sign in through it. The type id must be one listIdentityProviderTypes shows. Keycloak checks the config keys and values. A clientSecret in the config is stored and never shown again. Returns the provider as getIdentityProvider does. Refused for the master realm.")
    IdentityProviderDetail createIdentityProvider(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Alias, unique in the realm and part of the broker redirect URI, for example corporate-oidc") @NotBlank String alias,
            @ToolArg(description = "Type id, for example oidc, keycloak-oidc, saml or google") @NotBlank String providerId,
            @ToolArg(description = "Config as string keys and values, e.g. {\"clientId\": \"acme\", \"clientSecret\": \"...\", \"issuer\": \"https://idp.example\", \"authorizationUrl\": \"https://idp.example/auth\", \"tokenUrl\": \"https://idp.example/token\"}") @NotNull Map<String, String> config,
            @ToolArg(description = "Name on the login page") Optional<String> displayName,
            @ToolArg(description = "false to create the provider disabled; default true") Optional<Boolean> enabled,
            @ToolArg(description = "true to treat emails from this provider as verified") Optional<Boolean> trustEmail,
            @ToolArg(description = "true to store the provider's tokens for the user") Optional<Boolean> storeToken,
            @ToolArg(description = "true to allow only linking existing accounts, not signing in") Optional<Boolean> linkOnly,
            @ToolArg(description = "true to hide the provider on the login page") Optional<Boolean> hideOnLogin,
            @ToolArg(description = "Flow run after a user's first sign-in through the provider, for example first broker login; unset, the realm's first broker login flow runs") Optional<String> firstBrokerLoginFlowAlias,
            @ToolArg(description = "Flow run after every sign-in through the provider") Optional<String> postBrokerLoginFlowAlias) {
        return providers.create(realm, alias, providerId, config, input(displayName, enabled, trustEmail, storeToken,
                linkOnly, hideOnLogin, firstBrokerLoginFlowAlias, postBrokerLoginFlowAlias));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update an identity provider by alias. Supplied fields and config keys change; every other field and key keeps its value, including a stored clientSecret. The alias and type cannot change. Disabling an enabled provider stops every sign-in through it, so it "
            + Confirmation.ASKS_LOWER + " Returns the provider as getIdentityProvider does. Refused for the master realm.")
    IdentityProviderDetail updateIdentityProvider(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias,
            @ToolArg(description = "Config keys to change, as string keys and values, e.g. {\"tokenUrl\": \"https://idp.example/v2/token\"}", required = false) Map<String, String> config,
            @ToolArg(description = "Name on the login page") Optional<String> displayName,
            @ToolArg(description = "false to disable the provider (asks the user to confirm), true to enable it") Optional<Boolean> enabled,
            @ToolArg(description = "true to treat emails from this provider as verified") Optional<Boolean> trustEmail,
            @ToolArg(description = "true to store the provider's tokens for the user") Optional<Boolean> storeToken,
            @ToolArg(description = "true to allow only linking existing accounts, not signing in") Optional<Boolean> linkOnly,
            @ToolArg(description = "true to hide the provider on the login page") Optional<Boolean> hideOnLogin,
            @ToolArg(description = "Flow run after a user's first sign-in through the provider") Optional<String> firstBrokerLoginFlowAlias,
            @ToolArg(description = "Flow run after every sign-in through the provider") Optional<String> postBrokerLoginFlowAlias,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        return providers.update(realm, alias, config == null ? Map.of() : config, input(displayName, enabled,
                trustEmail, storeToken, linkOnly, hideOnLogin, firstBrokerLoginFlowAlias, postBrokerLoginFlowAlias),
                () -> confirmation.require("updateIdentityProvider", elicitation, confirm, "Disable identity provider "
                        + alias + " in realm " + realm + "? Every sign-in through it stops.",
                        "Disabling identity provider " + alias + " stops every sign-in through it in realm " + realm
                                + ". Call again with confirm=true to proceed."));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete an identity provider by alias. Users lose their links to it, and it disappears from the login page. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteIdentityProvider(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteIdentityProvider", elicitation, confirm,
                "Delete identity provider " + alias + " in realm " + realm + "? Users lose their links to it.");
        providers.delete(realm, alias);
        return "Deleted identity provider " + alias + " in realm " + realm;
    }

    private static IdentityProviderInput input(Optional<String> displayName, Optional<Boolean> enabled,
            Optional<Boolean> trustEmail, Optional<Boolean> storeToken, Optional<Boolean> linkOnly,
            Optional<Boolean> hideOnLogin, Optional<String> firstBrokerLoginFlowAlias,
            Optional<String> postBrokerLoginFlowAlias) {
        return new IdentityProviderInput(displayName.orElse(null), enabled.orElse(null), trustEmail.orElse(null),
                storeToken.orElse(null), linkOnly.orElse(null), hideOnLogin.orElse(null),
                firstBrokerLoginFlowAlias.orElse(null), postBrokerLoginFlowAlias.orElse(null));
    }
}
