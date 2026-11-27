package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserSessionRepresentation;

import io.github.filippobuletto.keycloakmcp.model.SessionInfo;
import io.github.filippobuletto.keycloakmcp.model.SessionStat;
import io.github.filippobuletto.keycloakmcp.model.SessionType;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class SessionService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientService clients;

    public List<SessionStat> stats(String realm) {
        return rawStats(realm).stream()
                .map(row -> new SessionStat(row.get("clientId"), count(row, "active"), count(row, "offline")))
                .toList();
    }

    /**
     * The user's regular sessions, then the offline ones. Keycloak lists offline sessions per client UUID only, so
     * they are read for each client the stats show with offline sessions; a session covering several clients is listed
     * under each, with that client's last access, and appears here once with the latest.
     */
    public List<SessionInfo> forUser(String realm, String userId) {
        return KeycloakErrors.call(() -> {
            List<Map<String, String>> stats = rawStats(realm);
            UserResource user = existingUser(realm, userId);
            List<SessionInfo> sessions = new ArrayList<>(user.getUserSessions().stream()
                    .map(s -> info(s, SessionType.REGULAR))
                    .toList());
            Map<String, SessionInfo> offline = new LinkedHashMap<>();
            for (Map<String, String> row : stats) {
                if (count(row, "offline") > 0) {
                    user.getOfflineSessions(row.get("id")).forEach(s -> offline.merge(s.getId(),
                            info(s, SessionType.OFFLINE),
                            (a, b) -> a.lastAccess() >= b.lastAccess() ? a : b));
                }
            }
            sessions.addAll(offline.values());
            return sessions;
        });
    }

    public List<SessionInfo> forClient(String realm, String clientId, SessionType type, int first, int max) {
        ClientResource client = keycloak.realm(realm).clients().get(clients.find(realm, clientId).getId());
        return KeycloakErrors.call(() -> (type == SessionType.OFFLINE
                ? client.getOfflineUserSessions(first, max)
                : client.getUserSessions(first, max)).stream()
                .map(s -> info(s, type))
                .toList());
    }

    /**
     * Ends one session. Keycloak answers 404 when {@code offline} does not match the session's type, so a 404 looks the
     * id up among the other type to say which value to use; the delete is never retried.
     */
    public void delete(String realm, String sessionId, boolean offline) {
        KeycloakErrors.run(() -> {
            try {
                keycloak.realm(realm).deleteSession(sessionId, offline);
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 404) {
                    throw e;
                }
                if (listedAs(realm, sessionId, !offline)) {
                    throw new ToolCallException("Session " + sessionId + " in realm " + realm + " is "
                            + (offline ? "regular" : "offline") + ". Nothing was ended. Call deleteSession again with offline="
                            + !offline + ".");
                }
                throw new ToolCallException("No session has id " + sessionId + " in realm " + realm
                        + ". Find session ids with listUserSessions or listClientSessions.");
            }
        });
    }

    /**
     * Logs a user out: Keycloak's user logout ends the regular sessions and rejects older tokens but leaves the offline
     * sessions listed, so they are deleted too.
     *
     * @param confirm takes the question with the session counts read just before; it throws to refuse
     * @return what ended
     */
    public String logoutUser(String realm, String userId, Consumer<String> confirm) {
        List<SessionInfo> sessions = forUser(realm, userId);
        return KeycloakErrors.call(() -> {
            UserResource user = keycloak.realm(realm).users().get(userId);
            String username = user.toRepresentation().getUsername();
            long regular = sessions.stream().filter(s -> s.type() == SessionType.REGULAR).count();
            List<String> offline = sessions.stream().filter(s -> s.type() == SessionType.OFFLINE)
                    .map(SessionInfo::id).toList();
            confirm.accept("Log out user " + username + " in realm " + realm + "? This ends " + regular
                    + " regular and " + quantity(offline.size(), "offline session")
                    + ", and Keycloak rejects the user's tokens issued before now.");
            user.logout();
            long ended = offline.stream().filter(id -> endOffline(realm, id)).count();
            return "Logged out user " + username + " in realm " + realm + ": ended " + regular + " regular and "
                    + quantity(ended, "offline session") + ".";
        });
    }

    /** False when the session ended between the count and the delete. */
    private boolean endOffline(String realm, String sessionId) {
        try {
            keycloak.realm(realm).deleteSession(sessionId, true);
            return true;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Ends every regular and offline session in the realm; Keycloak also rejects every token issued before now.
     *
     * @param confirm takes the question with the session counts read just before; it throws to refuse
     * @return what ended, as counted before the logout
     */
    public String logoutRealm(String realm, Consumer<String> confirm) {
        List<SessionStat> stats = stats(realm);
        String counted = stats.stream().mapToLong(SessionStat::active).sum() + " regular and "
                + quantity(stats.stream().mapToLong(SessionStat::offline).sum(), "offline session") + " across "
                + quantity(stats.size(), "client");
        confirm.accept("Log out every user in realm " + realm + "? This ends every session (the stats count "
                + counted + "), and Keycloak rejects every token issued before now.");
        KeycloakErrors.run(() -> keycloak.realm(realm).logoutAll());
        return "Logged out every user in realm " + realm + ". Before the logout the stats counted " + counted + ".";
    }

    /** "1 client", "2 clients": the noun takes an s unless the count is 1. */
    static String quantity(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /** Whether a client with sessions of the type lists the id; an unknown realm fails here with Keycloak's 404. */
    private boolean listedAs(String realm, String sessionId, boolean offline) {
        String key = offline ? "offline" : "active";
        return rawStats(realm).stream().filter(row -> count(row, key) > 0).anyMatch(row -> {
            ClientResource client = keycloak.realm(realm).clients().get(row.get("id"));
            int max = (int) count(row, key);
            return (offline ? client.getOfflineUserSessions(0, max) : client.getUserSessions(0, max)).stream()
                    .anyMatch(s -> sessionId.equals(s.getId()));
        });
    }

    private List<Map<String, String>> rawStats(String realm) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).getClientSessionStats());
    }

    /** Fails naming the user when the id is unknown; Keycloak only says "User not found". */
    private UserResource existingUser(String realm, String userId) {
        UserResource user = keycloak.realm(realm).users().get(userId);
        try {
            user.toRepresentation();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw new ToolCallException("User " + userId + " not found in realm " + realm
                        + ". Find the id with searchUsers.");
            }
            throw e;
        }
        return user;
    }

    /** Keycloak keys {@code clients} by client UUID; only the clientIds are kept. */
    static SessionInfo info(UserSessionRepresentation session, SessionType type) {
        return new SessionInfo(session.getId(), type, session.getUsername(), session.getUserId(),
                session.getIpAddress(), session.getStart(), session.getLastAccess(), session.isRememberMe(),
                session.getClients() == null ? List.of() : session.getClients().values().stream().sorted().toList());
    }

    /** Keycloak sends the counts as strings. */
    private static long count(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null ? 0 : Long.parseLong(value);
    }
}
