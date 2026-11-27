package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.keycloak.representations.idm.RealmEventsConfigRepresentation;

import io.github.filippobuletto.keycloakmcp.guard.Confirmation;
import io.github.filippobuletto.keycloakmcp.guard.ProtectMaster;
import io.github.filippobuletto.keycloakmcp.guard.RequireConfirmFlag;
import io.github.filippobuletto.keycloakmcp.model.AdminEventInfo;
import io.github.filippobuletto.keycloakmcp.model.EventInfo;
import io.github.filippobuletto.keycloakmcp.model.EventKind;
import io.github.filippobuletto.keycloakmcp.model.EventSettingsInput;
import io.github.filippobuletto.keycloakmcp.service.EventService;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;

@Singleton
public class EventTools {

    @Inject
    EventService events;

    @Inject
    Confirmation confirmation;

    @Tool(description = "List a realm's login (user) events, newest first: sign-ins, sign-in errors, logouts, token and account actions, as the realm's event settings record them (none while eventsEnabled is false). Each shows time (epoch milliseconds), type, clientId, userId, sessionId, ipAddress, error (on _ERROR types) and details. Every filter given must match. Returns at most `max` events starting at `first`.")
    List<EventInfo> listEvents(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Event types, for example [\"LOGIN\", \"LOGIN_ERROR\"]") Optional<List<String>> types,
            @ToolArg(description = "Client id, for example account-console") Optional<String> clientId,
            @ToolArg(description = "User id (UUID)") Optional<String> userId,
            @ToolArg(description = "IP address") Optional<String> ipAddress,
            @ToolArg(description = "First day, yyyy-MM-dd, or epoch milliseconds (inclusive)") Optional<String> dateFrom,
            @ToolArg(description = "Last day, yyyy-MM-dd, or epoch milliseconds (inclusive)") Optional<String> dateTo,
            @ToolArg(description = "Number of results to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max results (1-500)", defaultValue = "50") @Min(1) @Max(500) int max) {
        return events.list(realm, types.orElse(null), clientId.orElse(null), userId.orElse(null),
                ipAddress.orElse(null), dateFrom.orElse(null), dateTo.orElse(null), first, max);
    }

    @Tool(description = "List a realm's admin events, newest first: changes made through the admin API or console, as the realm's event settings record them (none while adminEventsEnabled is false). Each shows time (epoch milliseconds), operationType, resourceType, resourcePath, who acted (authRealm by name, authClientId, authUserId, authIpAddress) and error. Every filter given must match. Returns at most `max` events starting at `first`.")
    List<AdminEventInfo> listAdminEvents(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "Operation types: CREATE, UPDATE, DELETE, ACTION") Optional<List<String>> operationTypes,
            @ToolArg(description = "Resource types, for example [\"USER\", \"GROUP\", \"CLIENT\"]") Optional<List<String>> resourceTypes,
            @ToolArg(description = "Resource path; * matches any part, for example users/* or groups/*") Optional<String> resourcePath,
            @ToolArg(description = "Realm the acting admin signed in to, by name, for example master") Optional<String> authRealm,
            @ToolArg(description = "Client the acting admin used, by clientId, for example admin-cli; needs authRealm") Optional<String> authClientId,
            @ToolArg(description = "Acting admin's user id (UUID)") Optional<String> authUserId,
            @ToolArg(description = "Acting admin's IP address") Optional<String> authIpAddress,
            @ToolArg(description = "First day, yyyy-MM-dd, or epoch milliseconds (inclusive)") Optional<String> dateFrom,
            @ToolArg(description = "Last day, yyyy-MM-dd, or epoch milliseconds (inclusive)") Optional<String> dateTo,
            @ToolArg(description = "Number of results to skip, for paging", defaultValue = "0") @Min(0) int first,
            @ToolArg(description = "Max results (1-500)", defaultValue = "50") @Min(1) @Max(500) int max,
            @ToolArg(description = "true to add the JSON sent with each change (recorded only while adminEventsDetailsEnabled is true)", defaultValue = "false") boolean includeRepresentation) {
        return events.listAdmin(realm, operationTypes.orElse(null), resourceTypes.orElse(null),
                resourcePath.orElse(null), authRealm.orElse(null), authClientId.orElse(null), authUserId.orElse(null),
                authIpAddress.orElse(null), dateFrom.orElse(null), dateTo.orElse(null), first, max,
                includeRepresentation);
    }

    @Tool(description = "Show a realm's event settings: eventsEnabled (login events recorded), eventsExpiration (seconds login events are kept; missing or 0 keeps them), eventsListeners, enabledEventTypes (the login event types recorded; Keycloak's defaults when none were chosen), adminEventsEnabled (admin changes recorded), adminEventsDetailsEnabled (admin events keep the JSON sent).")
    RealmEventsConfigRepresentation getEventSettings(
            @ToolArg(description = "Realm name") @NotBlank String realm) {
        return events.settings(realm);
    }

    @ToolGuardrails(input = ProtectMaster.class)
    @Tool(description = "Change a realm's event settings. Only the settings given change; the others keep their values. A change that stops recording (turns login events or admin events off, or removes a listener) "
            + Confirmation.ASKS_LOWER
            + " Returns the settings as getEventSettings shows them. Refused for the master realm.")
    RealmEventsConfigRepresentation updateEventSettings(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "true to record login events, false to stop (asks the user to confirm)") Optional<Boolean> eventsEnabled,
            @ToolArg(description = "Seconds to keep login events; 0 keeps them") Optional<@Min(0) Long> eventsExpiration,
            @ToolArg(description = "Event listener ids, replacing the current list, for example [\"jboss-logging\"]; removing one asks the user to confirm") Optional<List<String>> eventsListeners,
            @ToolArg(description = "Login event types to record, replacing the current list, for example [\"LOGIN\", \"LOGIN_ERROR\"]") Optional<List<String>> enabledEventTypes,
            @ToolArg(description = "true to record admin changes, false to stop (asks the user to confirm)") Optional<Boolean> adminEventsEnabled,
            @ToolArg(description = "true to keep the JSON sent with each admin event") Optional<Boolean> adminEventsDetailsEnabled,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        EventSettingsInput change = new EventSettingsInput(eventsEnabled.orElse(null), eventsExpiration.orElse(null),
                eventsListeners.orElse(null), enabledEventTypes.orElse(null), adminEventsEnabled.orElse(null),
                adminEventsDetailsEnabled.orElse(null));
        return events.updateSettings(realm, change, consequences -> confirmation.require("updateEventSettings",
                elicitation, confirm, consequences + " Proceed?", consequences + " Call again with confirm=true to proceed."));
    }

    @ToolGuardrails(input = { ProtectMaster.class, RequireConfirmFlag.class })
    @Tool(description = "Delete every stored event of one kind in a realm: LOGIN (login events) or ADMIN (admin events). Recording goes on as the event settings say. "
            + Confirmation.ASKS + " Refused for the master realm.")
    String clearEvents(
            @ToolArg(description = "Realm name") @NotBlank String realm,
            @ToolArg(description = "LOGIN or ADMIN") EventKind kind,
            @ToolArg(description = Confirmation.FLAG_ARG, defaultValue = "false") boolean confirm,
            Elicitation elicitation) {
        return events.clear(realm, kind,
                question -> confirmation.require("clearEvents", elicitation, confirm, question));
    }
}
