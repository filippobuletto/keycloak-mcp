package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.SessionInfo;
import io.github.filippobuletto.keycloakmcp.model.SessionStat;
import io.github.filippobuletto.keycloakmcp.model.SessionType;
import io.github.filippobuletto.keycloakmcp.service.SessionService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class SessionTools {

    @Inject
    SessionService sessions;

    @Inject
    Confirmation confirmation;

    @Tool(description = "List every session of one user: REGULAR sessions (browser or app sign-ins) first, then OFFLINE sessions (behind offline tokens, which survive logout of the regular session). Each shows id, type, IP address, start and lastAccess (epoch milliseconds), rememberMe, and the clientIds it covers.")
    List<SessionInfo> listUserSessions(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId) {
        return sessions.forUser(realm, userId);
    }

    @Tool(description = "List the sessions of one client, of one type, as listUserSessions shows them. Returns at most `max` sessions starting at `first`.")
    List<SessionInfo> listClientSessions(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Client id, for example account-console") @NotBlank String clientId,
            @ToolArg(description = "REGULAR or OFFLINE", defaultValue = "REGULAR") SessionType type,
            @ToolArg(description = "Number of results to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max results (1-500)", defaultValue = "50") @Min(1) @Max(500) int max) {
        return sessions.forClient(realm, clientId, type, first, max);
    }

    @Tool(description = "Count the sessions of each client in a realm that has any: clientId, active (regular) sessions, offline sessions.")
    List<SessionStat> getSessionStats(
            @ToolArg(description = "Realm name") @NotBlank String realm) {
        return sessions.stats(realm);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "End one session by id, as listUserSessions or listClientSessions show it. Its refresh tokens stop working; the user can sign in again. Set offline=true for an OFFLINE session; a wrong value ends nothing and the error says which value to use. Refused for the master realm.")
    String deleteSession(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Session id") @NotBlank String sessionId,
            @ToolArg(description = "true for an OFFLINE session, false for a REGULAR one", defaultValue = "false") boolean offline) {
        sessions.delete(realm, sessionId, offline);
        return "Ended " + (offline ? "offline" : "regular") + " session " + sessionId + " in realm " + realm;
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Log out one user: end all their regular and offline sessions; Keycloak also rejects the user's tokens issued before now. The user must sign in again on every device. "
            + Confirmation.ASKS + " Returns how many sessions ended. Refused for the master realm.")
    String logoutUser(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "User id (UUID)") @NotBlank String userId,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        return sessions.logoutUser(realm, userId,
                question -> confirmation.require("logoutUser", elicitation, confirm, question));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Log out every user in a realm: end every regular and offline session; Keycloak also rejects every token issued before now, so every user and app must sign in again. "
            + Confirmation.ASKS + " Returns the session counts read before the logout. Refused for the master realm.")
    String logoutRealm(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        return sessions.logoutRealm(realm,
                question -> confirmation.require("logoutRealm", elicitation, confirm, question));
    }
}
