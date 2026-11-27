package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;

import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.RealmDetail;
import io.github.filippobuletto.keycloakmcp.model.RealmSummary;
import io.github.filippobuletto.keycloakmcp.model.ServerVersion;
import io.github.filippobuletto.keycloakmcp.service.RealmService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class RealmTools {

    @Inject
    Confirmation confirmation;

    @Inject
    RealmService realms;

    @Tool(description = "List all realms visible to the identity the server acts as: the signed-in user over HTTP, the configured admin over stdio.")
    List<RealmSummary> listRealms() {
        return realms.list();
    }

    @Tool(description = "Get the version of the Keycloak server.")
    ServerVersion getServerVersion() {
        return realms.version();
    }

    @Tool(description = "Get a realm's settings: display name, enabled flag, login settings, token and session lifespans (seconds), refresh token revocation, password policy, brute force detection, admin event expiry (seconds) and the user profile's unmanaged attribute policy.")
    RealmDetail getRealm(@ToolArg(description = "Realm name") @NotBlank String realm) {
        return realms.get(realm);
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Create a realm with Keycloak's default settings. Realm-level change. " + Confirmation.ASKS)
    RealmDetail createRealm(
            @ToolArg(description = "Name of the new realm, used in URLs") @NotBlank String realm,
            @ToolArg(description = "Name shown on login pages") Optional<String> displayName,
            @ToolArg(description = "Whether users can log in; default true") Optional<Boolean> enabled,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("createRealm", elicitation, confirm,
                "Create realm " + realm + "? Realm-level change.");
        return realms.create(realm, displayName.orElse(null), enabled.orElse(true));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete a realm with all its users, clients, roles and groups. Irreversible. " + Confirmation.ASKS
            + " The master realm cannot be deleted.")
    String deleteRealm(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        confirmation.require("deleteRealm", elicitation, confirm,
                "Delete realm " + realm + " with all its users, clients, roles and groups? Irreversible.");
        realms.delete(realm);
        return "Deleted realm " + realm;
    }
}
