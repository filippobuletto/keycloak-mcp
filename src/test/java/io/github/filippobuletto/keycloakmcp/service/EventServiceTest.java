package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.AuthDetailsRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;

import io.github.filippobuletto.keycloakmcp.model.AdminEventInfo;
import io.github.filippobuletto.keycloakmcp.model.EventSettingsInput;

class EventServiceTest {

    static final EventSettingsInput NOTHING = new EventSettingsInput(null, null, null, null, null, null);

    /** Login and admin events on, kept 3600 s, listeners jboss-logging and email. */
    static RealmEventsConfigRepresentation recording() {
        RealmEventsConfigRepresentation current = new RealmEventsConfigRepresentation();
        current.setEventsEnabled(true);
        current.setEventsExpiration(3600L);
        current.setEventsListeners(List.of("jboss-logging", "email"));
        current.setEnabledEventTypes(List.of("LOGIN", "LOGIN_ERROR"));
        current.setAdminEventsEnabled(true);
        current.setAdminEventsDetailsEnabled(true);
        return current;
    }

    @Test
    void bodyCarriesTheTwoResetFieldsAndLeavesTheRestNull() {
        RealmEventsConfigRepresentation body = EventService.body(recording(),
                new EventSettingsInput(null, null, null, null, false, null));

        assertTrue(body.isEventsEnabled());
        assertEquals(3600L, body.getEventsExpiration());
        assertNull(body.getEventsListeners());
        assertNull(body.getEnabledEventTypes());
        assertEquals(false, body.isAdminEventsEnabled());
        assertNull(body.isAdminEventsDetailsEnabled());
    }

    @Test
    void bodyTakesEveryGivenValue() {
        RealmEventsConfigRepresentation body = EventService.body(recording(), new EventSettingsInput(false, 60L,
                List.of("jboss-logging"), List.of("LOGIN"), false, false));

        assertFalse(body.isEventsEnabled());
        assertEquals(60L, body.getEventsExpiration());
        assertEquals(List.of("jboss-logging"), body.getEventsListeners());
        assertEquals(List.of("LOGIN"), body.getEnabledEventTypes());
        assertEquals(false, body.isAdminEventsEnabled());
        assertEquals(false, body.isAdminEventsDetailsEnabled());
    }

    @Test
    void bodyKeepsAnUnsetExpirationUnset() {
        RealmEventsConfigRepresentation current = new RealmEventsConfigRepresentation();

        assertNull(EventService.body(current, NOTHING).getEventsExpiration());
    }

    @Test
    void stopsListsEveryChangeThatStopsRecording() {
        List<String> stops = EventService.stops(recording(),
                new EventSettingsInput(false, null, List.of("jboss-logging"), null, false, null));

        assertEquals(List.of("turns login events off", "turns admin events off", "removes the listener email"),
                stops);
    }

    @Test
    void stopsIsEmptyWhenNothingStops() {
        assertEquals(List.of(), EventService.stops(recording(), NOTHING));
        assertEquals(List.of(), EventService.stops(recording(), new EventSettingsInput(true, 60L,
                List.of("jboss-logging", "email", "workflow-event-listener"), List.of("LOGIN"), true, false)));
    }

    static AdminEventRepresentation adminEvent() {
        AuthDetailsRepresentation auth = new AuthDetailsRepresentation();
        auth.setRealmId("master-id");
        auth.setClientId("cli-uuid");
        auth.setUserId("admin-id");
        auth.setIpAddress("10.0.0.1");
        AdminEventRepresentation event = new AdminEventRepresentation();
        event.setTime(42L);
        event.setAuthDetails(auth);
        event.setOperationType("CREATE");
        event.setResourceType("GROUP");
        event.setResourcePath("groups/g1");
        event.setRepresentation("{\"name\":\"g1\"}");
        return event;
    }

    @Test
    void adminInfoShowsWhoActedByNameAndTheJsonOnlyWhenAsked() {
        AdminEventInfo info = EventService.adminInfo(adminEvent(), Map.of("master-id", "master"),
                (realm, uuid) -> realm + "/" + uuid + "->admin-cli", false);

        assertEquals(new AdminEventInfo(42L, "CREATE", "GROUP", "groups/g1", "master", "master/cli-uuid->admin-cli",
                "admin-id", "10.0.0.1", null, null), info);
        assertEquals("{\"name\":\"g1\"}", EventService.adminInfo(adminEvent(), Map.of("master-id", "master"),
                (realm, uuid) -> "admin-cli", true).representation());
    }

    @Test
    void adminInfoShowsIdsThatNoLongerResolveAsTheyAre() {
        AdminEventInfo info = EventService.adminInfo(adminEvent(), Map.of(), (realm, uuid) -> "never asked", false);

        assertEquals("master-id", info.authRealm());
        assertEquals("cli-uuid", info.authClientId());
    }

    @Test
    void turningOffWhatIsAlreadyOffStopsNothing() {
        RealmEventsConfigRepresentation off = new RealmEventsConfigRepresentation();
        off.setEventsListeners(List.of("jboss-logging"));

        assertEquals(List.of(), EventService.stops(off, new EventSettingsInput(false, null, null, null, false, null)));
    }
}
