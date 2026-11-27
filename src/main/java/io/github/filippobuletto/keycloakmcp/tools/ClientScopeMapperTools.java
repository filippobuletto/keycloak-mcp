package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.ClientScopeDetail;
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.github.filippobuletto.keycloakmcp.service.ClientScopeMapperService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class ClientScopeMapperTools {

    @Inject
    Confirmation confirmation;

    @Inject
    ClientScopeMapperService mappers;

    @Tool(description = "List the OpenID Connect protocol mapper types of the Keycloak server: type id, name, category, help text, and the config keys (properties) a mapper of that type takes, with label, type, default value and options.")
    List<ProtocolMapperType> listProtocolMapperTypes() {
        return mappers.types();
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Add a protocol mapper to an OpenID Connect client scope, so every client that uses the scope gets the claim it maps. The mapper type and config keys must be ones listProtocolMapperTypes shows; unknown ones are refused. Returns the scope as getClientScope does. Refused for the master realm and for SAML scopes.")
    ClientScopeDetail addClientScopeMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name, for example billing:read") @NotBlank String scope,
            @ToolArg(description = "Mapper name, unique in the scope, for example tenant") @NotBlank String name,
            @ToolArg(description = "Mapper type id, for example oidc-hardcoded-claim-mapper") @NotBlank String mapperType,
            @ToolArg(description = "Config as string keys and values, e.g. {\"claim.name\": \"tenant\", \"claim.value\": \"acme\", \"access.token.claim\": \"true\"}") @NotNull Map<String, String> config) {
        return mappers.add(realm, scope, name, mapperType, config);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Update the config of a protocol mapper of an OpenID Connect client scope. Supplied keys change; every other key keeps its value. The name and type cannot change. Config keys must be ones listProtocolMapperTypes shows for the mapper's type. Returns the scope as getClientScope does. Refused for the master realm and for SAML scopes.")
    ClientScopeDetail updateClientScopeMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String scope,
            @ToolArg(description = "Mapper name") @NotBlank String name,
            @ToolArg(description = "Config keys to change, as string keys and values, e.g. {\"claim.value\": \"globex\"}") @NotNull Map<String, String> config) {
        return mappers.update(realm, scope, name, config);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a protocol mapper from an OpenID Connect client scope. Its claim leaves the tokens of every client that uses the scope. "
            + Confirmation.ASKS + " Refused for the master realm and for SAML scopes.")
    String deleteClientScopeMapper(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client scope name") @NotBlank String scope,
            @ToolArg(description = "Mapper name") @NotBlank String name,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteClientScopeMapper", elicitation, confirm,
                "Delete protocol mapper " + name + " from client scope " + scope + " in realm " + realm
                        + "? Its claim leaves the tokens of every client that uses the scope.");
        mappers.delete(realm, scope, name);
        return "Deleted protocol mapper " + name + " from client scope " + scope + " in realm " + realm;
    }
}
