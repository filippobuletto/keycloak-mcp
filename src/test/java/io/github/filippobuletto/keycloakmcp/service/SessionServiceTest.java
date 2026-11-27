package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SessionServiceTest {

    @Test
    void quantityUsesTheSingularForOne() {
        assertEquals("1 client", SessionService.quantity(1, "client"));
        assertEquals("1 offline session", SessionService.quantity(1, "offline session"));
    }

    @Test
    void quantityUsesThePluralForZeroAndMany() {
        assertEquals("0 clients", SessionService.quantity(0, "client"));
        assertEquals("2 offline sessions", SessionService.quantity(2, "offline session"));
    }
}
