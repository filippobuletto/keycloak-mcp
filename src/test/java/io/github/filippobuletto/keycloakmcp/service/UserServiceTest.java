package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

import io.github.filippobuletto.keycloakmcp.model.UserInput;

class UserServiceTest {

    @Test
    void wrapsPlainTermForSubstringMatch() {
        assertEquals("*corp.com*", UserService.query("corp.com"));
    }

    @Test
    void trimsPlainTerm() {
        assertEquals("*jdoe*", UserService.query("  jdoe "));
    }

    @Test
    void keepsExplicitWildcard() {
        assertEquals("j*", UserService.query("j*"));
    }

    @Test
    void keepsExactQuotedTerm() {
        assertEquals("\"jdoe\"", UserService.query("\"jdoe\""));
    }

    @Test
    void blankListsAllUsers() {
        assertNull(UserService.query("  "));
        assertNull(UserService.query(null));
    }

    static UserRepresentation jdoe() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("jdoe");
        user.setEmail("jdoe@corp.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEnabled(true);
        user.setAttributes(new HashMap<>(Map.of("department", List.of("finance"), "site", List.of("milan"))));
        return user;
    }

    @Test
    void applyChangesOnlySuppliedFields() {
        UserRepresentation user = jdoe();

        UserService.apply(user, new UserInput(null, "Johnny", null, false, null));

        assertEquals("jdoe@corp.com", user.getEmail());
        assertEquals("Johnny", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals(false, user.isEnabled());
        assertEquals(Map.of("department", List.of("finance"), "site", List.of("milan")), user.getAttributes());
    }

    @Test
    void applyMergesSuppliedAttributes() {
        UserRepresentation user = jdoe();

        UserService.apply(user, new UserInput(null, null, null, null, Map.of("site", List.of("rome"))));

        assertEquals(Map.of("department", List.of("finance"), "site", List.of("rome")), user.getAttributes());
        assertTrue(user.isEnabled());
    }

    @Test
    void applyCreatesAttributesWhenAbsent() {
        UserRepresentation user = new UserRepresentation();

        UserService.apply(user, new UserInput("a@b.c", null, null, null, Map.of("site", List.of("rome"))));

        assertEquals("a@b.c", user.getEmail());
        assertEquals(Map.of("site", List.of("rome")), user.getAttributes());
    }
}
