package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsOriginsTest {

    static final String REGEX = "/https://[a-z]+\\.corp\\.example/";

    @Test
    void bareWildcardIsRefused() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CorsOrigins.regexEntries(List.of("*")));
        assertTrue(error.getMessage().contains("MCP_CORS_ORIGINS"), error.getMessage());
    }

    @Test
    void wildcardInsideAListIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> CorsOrigins.regexEntries(List.of("https://a.example", " * ")));
    }

    @Test
    void exactOriginsPassWithoutWarnings() {
        assertEquals(List.of(), CorsOrigins.regexEntries(List.of("https://a.example", "http://localhost:3000")));
    }

    @Test
    void regexOriginsAreReturnedForAWarning() {
        assertEquals(List.of(REGEX), CorsOrigins.regexEntries(List.of("https://a.example", REGEX)));
    }

    @Test
    void emptyListPasses() {
        assertEquals(List.of(), CorsOrigins.regexEntries(List.of()));
    }
}
