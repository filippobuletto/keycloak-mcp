package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.AuthDetailsRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

import io.github.filippobuletto.keycloakmcp.model.AdminEventInfo;
import io.github.filippobuletto.keycloakmcp.model.EventInfo;
import io.github.filippobuletto.keycloakmcp.model.EventKind;
import io.github.filippobuletto.keycloakmcp.model.EventSettingsInput;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class EventService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientService clients;

    public RealmEventsConfigRepresentation settings(String realm) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).getRealmEventsConfig());
    }

    /**
     * Changes only the given settings. Names are checked against the server first, because Keycloak stores unknown
     * event types and rejects an unknown listener without naming it.
     *
     * @param confirm takes what the change stops, when it stops recording; it throws to refuse
     * @return the settings read back
     */
    public RealmEventsConfigRepresentation updateSettings(String realm, EventSettingsInput change,
            Consumer<String> confirm) {
        RealmEventsConfigRepresentation current = settings(realm);
        ServerInfoRepresentation info = serverInfo();
        known("event type", change.enabledEventTypes(), info.getEnums().get("eventType"));
        known("event listener", change.eventsListeners(),
                info.getProviders().get("eventsListener").getProviders().keySet());
        List<String> stops = stops(current, change);
        if (!stops.isEmpty()) {
            confirm.accept("This change to the event settings of realm " + realm + " " + String.join(", ", stops)
                    + "; events from then on are not recorded.");
        }
        KeycloakErrors.run(() -> keycloak.realm(realm).updateRealmEventsConfig(body(current, change)));
        return settings(realm);
    }

    /**
     * Login events, newest first. Types are checked against the server first, because Keycloak answers an unknown type
     * with a 500.
     */
    public List<EventInfo> list(String realm, List<String> types, String clientId, String userId, String ipAddress,
            String dateFrom, String dateTo, int first, int max) {
        known("event type", types, serverInfo().getEnums().get("eventType"));
        return KeycloakErrors.call(() -> keycloak.realm(realm)
                .getEvents(types, clientId, userId, dateFrom, dateTo, ipAddress, first, max, null).stream()
                .map(e -> new EventInfo(e.getTime(), e.getType(), e.getClientId(), e.getUserId(), e.getSessionId(),
                        e.getIpAddress(), e.getError(), e.getDetails()))
                .toList());
    }

    /**
     * Admin events, newest first. Keycloak filters who acted by ids only, so {@code authRealm} (a realm name) and
     * {@code authClientId} (a clientId in that realm) are resolved first; results show them by name again.
     */
    public List<AdminEventInfo> listAdmin(String realm, List<String> operationTypes, List<String> resourceTypes,
            String resourcePath, String authRealm, String authClientId, String authUserId, String authIpAddress,
            String dateFrom, String dateTo, int first, int max, boolean withRepresentation) {
        if (authClientId != null && authRealm == null) {
            throw new ToolCallException("authClientId needs authRealm, the realm the client belongs to"
                    + " (master for an admin signed in to master).");
        }
        ServerInfoRepresentation info = serverInfo();
        known("operation type", operationTypes, info.getEnums().get("operationType"));
        known("resource type", resourceTypes, info.getEnums().get("resourceType"));
        Map<String, String> realmNames = KeycloakErrors.call(() -> keycloak.realms().findAll()).stream()
                .collect(Collectors.toMap(RealmRepresentation::getId, RealmRepresentation::getRealm));
        String authRealmId = authRealm == null ? null
                : realmNames.entrySet().stream().filter(e -> e.getValue().equals(authRealm)).map(Map.Entry::getKey)
                        .findFirst().orElseThrow(() -> new ToolCallException("Realm " + authRealm + " not found"));
        String authClientUuid = authClientId == null ? null : clients.find(authRealm, authClientId).getId();
        Map<String, String> clientIds = new HashMap<>();
        BiFunction<String, String, String> clientIdOf = (realmName, uuid) -> clientIds
                .computeIfAbsent(realmName + "/" + uuid, key -> clientId(realmName, uuid));
        return KeycloakErrors.call(() -> keycloak.realm(realm)
                .getAdminEvents(operationTypes, authRealmId, authClientUuid, authUserId, authIpAddress, resourcePath,
                        resourceTypes, dateFrom, dateTo, first, max, null)
                .stream()
                .map(e -> adminInfo(e, realmNames, clientIdOf, withRepresentation))
                .toList());
    }

    /** The clientId of a client UUID, or the UUID when the client no longer exists. */
    private String clientId(String realm, String uuid) {
        try {
            return keycloak.realm(realm).clients().get(uuid).toRepresentation().getClientId();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return uuid;
            }
            throw e;
        }
    }

    /**
     * Shows who acted by realm name and clientId; a realm id missing from {@code realmNames} shows as is, with its
     * client UUID.
     */
    static AdminEventInfo adminInfo(AdminEventRepresentation event, Map<String, String> realmNames,
            BiFunction<String, String, String> clientIdOf, boolean withRepresentation) {
        AuthDetailsRepresentation auth = event.getAuthDetails();
        String realmName = realmNames.get(auth.getRealmId());
        String clientId = realmName != null && auth.getClientId() != null
                ? clientIdOf.apply(realmName, auth.getClientId())
                : auth.getClientId();
        return new AdminEventInfo(event.getTime(), event.getOperationType(), event.getResourceType(),
                event.getResourcePath(), realmName != null ? realmName : auth.getRealmId(), clientId, auth.getUserId(),
                auth.getIpAddress(), event.getError(), withRepresentation ? event.getRepresentation() : null);
    }

    /**
     * Deletes every stored event of one kind in the realm. The settings read first make an unknown realm fail before
     * the question.
     *
     * @param confirm takes the question; it throws to refuse
     * @return what was deleted
     */
    public String clear(String realm, EventKind kind, Consumer<String> confirm) {
        settings(realm);
        String what = (kind == EventKind.LOGIN ? "login" : "admin") + " events";
        confirm.accept("Delete all stored " + what + " in realm " + realm + "? This cannot be undone.");
        KeycloakErrors.run(() -> {
            if (kind == EventKind.LOGIN) {
                keycloak.realm(realm).clearEvents();
            } else {
                keycloak.realm(realm).clearAdminEvents();
            }
        });
        return "Deleted all stored " + what + " in realm " + realm + ".";
    }

    private ServerInfoRepresentation serverInfo() {
        return KeycloakErrors.call(() -> keycloak.serverInfo().getInfo());
    }

    private static void known(String what, List<String> names, Collection<String> known) {
        if (names == null) {
            return;
        }
        List<String> unknown = names.stream().filter(name -> !known.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw new ToolCallException("Unknown " + what + (unknown.size() == 1 ? "" : "s") + ": "
                    + String.join(", ", unknown) + ".");
        }
    }

    /**
     * The PUT body: Keycloak resets {@code eventsEnabled} and {@code eventsExpiration} when they are missing, so those
     * two fall back to the current settings; it keeps every other setting sent as null.
     */
    static RealmEventsConfigRepresentation body(RealmEventsConfigRepresentation current, EventSettingsInput change) {
        RealmEventsConfigRepresentation body = new RealmEventsConfigRepresentation();
        body.setEventsEnabled(change.eventsEnabled() != null ? change.eventsEnabled() : current.isEventsEnabled());
        body.setEventsExpiration(
                change.eventsExpiration() != null ? change.eventsExpiration() : current.getEventsExpiration());
        body.setEventsListeners(change.eventsListeners());
        body.setEnabledEventTypes(change.enabledEventTypes());
        body.setAdminEventsEnabled(change.adminEventsEnabled());
        body.setAdminEventsDetailsEnabled(change.adminEventsDetailsEnabled());
        return body;
    }

    /** What the change stops recording; empty when it stops nothing. */
    static List<String> stops(RealmEventsConfigRepresentation current, EventSettingsInput change) {
        List<String> stops = new ArrayList<>();
        if (current.isEventsEnabled() && Boolean.FALSE.equals(change.eventsEnabled())) {
            stops.add("turns login events off");
        }
        if (Boolean.TRUE.equals(current.isAdminEventsEnabled()) && Boolean.FALSE.equals(change.adminEventsEnabled())) {
            stops.add("turns admin events off");
        }
        if (change.eventsListeners() != null && current.getEventsListeners() != null) {
            current.getEventsListeners().stream().filter(listener -> !change.eventsListeners().contains(listener))
                    .forEach(listener -> stops.add("removes the listener " + listener));
        }
        return stops;
    }
}
