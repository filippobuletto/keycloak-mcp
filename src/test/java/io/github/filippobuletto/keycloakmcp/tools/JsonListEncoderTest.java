package io.github.filippobuletto.keycloakmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;

class JsonListEncoderTest {

    /** A bean Jackson cannot serialize: no properties, so the default mapper fails on it. */
    static class Opaque {
    }

    static JsonListEncoder encoder() {
        JsonListEncoder encoder = new JsonListEncoder();
        encoder.mapper = new ObjectMapper();
        return encoder;
    }

    @Test
    void supportsListsOnly() {
        assertTrue(encoder().supports(ArrayList.class));
        assertFalse(encoder().supports(Map.class));
    }

    @Test
    void encodesAnEmptyListAsAnEmptyArray() {
        ToolResponse response = encoder().encode(List.of());
        assertEquals("[]", ((TextContent) response.content().get(0)).text());
    }

    @Test
    void failsWithANamedErrorWhenAnElementCannotBeEncoded() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> encoder().encode(List.of(new Opaque())));
        assertEquals("Cannot encode tool result as JSON", error.getMessage());
        assertInstanceOf(JsonProcessingException.class, error.getCause());
    }
}
