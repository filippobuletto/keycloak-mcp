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
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.github.filippobuletto.keycloakmcp.model.SyncMode;
import io.github.filippobuletto.keycloakmcp.service.IdentityProviderMapperService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class IdentityProviderMapperTools {

    @Inject
    Confirmation confirmation;

    @Inject
    IdentityProviderMapperService mappers;

    @Tool(description = "List the mapper types an identity provider can use, which depend on its type (oidc, saml, google, ...): type id, name, category, help text, and the config keys (properties) a mapper of that type takes, with label, type, default value and options. getIdentityProvider shows the provider's mappers.")
    List<ProtocolMapperType> listIdentityProviderMapperTypes(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias) {
        return mappers.types(realm, alias);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add a mapper to an identity provider, so sign-ins through it copy data (attributes, roles, groups, username, session notes) into the local user. The mapper type and config keys must be ones listIdentityProviderMapperTypes shows for this provider; others are refused. Returns the provider as getIdentityProvider does. Refused for the master realm.")
    IdentityProviderDetail addIdentityProviderMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias,
            @ToolArg(description = "Mapper name, unique in the provider, for example department") @NotBlank String name,
            @ToolArg(description = "Mapper type id, for example oidc-user-attribute-idp-mapper") @NotBlank String mapperType,
            @ToolArg(description = "Config as string keys and values, without syncMode, e.g. {\"claim\": \"dept\", \"user.attribute\": \"department\"}") @NotNull Map<String, String> config,
            @ToolArg(description = "When the mapper applies: INHERIT uses the provider's sync mode, IMPORT only the first sign-in, FORCE every sign-in, LEGACY Keycloak's old behavior; default INHERIT") Optional<SyncMode> syncMode) {
        return mappers.add(realm, alias, name, mapperType, config, syncMode.orElse(SyncMode.INHERIT));
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update a mapper of an identity provider: supplied config keys change, every other key keeps its value, and the sync mode changes only when given. The name and type cannot change. Config keys must be ones listIdentityProviderMapperTypes shows for the mapper's type. Returns the provider as getIdentityProvider does. Refused for the master realm.")
    IdentityProviderDetail updateIdentityProviderMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias,
            @ToolArg(description = "Mapper name") @NotBlank String name,
            @ToolArg(description = "Config keys to change, without syncMode, e.g. {\"user.attribute\": \"dept\"}") Optional<Map<String, String>> config,
            @ToolArg(description = "New sync mode: INHERIT, LEGACY, IMPORT or FORCE; unchanged when omitted") Optional<SyncMode> syncMode) {
        return mappers.update(realm, alias, name, config.orElse(Map.of()), syncMode.orElse(null));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a mapper from an identity provider. Later sign-ins through the provider no longer apply it; data it already wrote to users stays. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String deleteIdentityProviderMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Identity provider alias") @NotBlank String alias,
            @ToolArg(description = "Mapper name") @NotBlank String name,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteIdentityProviderMapper", elicitation, confirm,
                "Delete mapper " + name + " from identity provider " + alias + " in realm " + realm
                        + "? Later sign-ins no longer apply it.");
        mappers.delete(realm, alias, name);
        return "Deleted mapper " + name + " from identity provider " + alias + " in realm " + realm;
    }
}
