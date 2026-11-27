package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusTest;

/** Without {@code MCP_PUBLIC_URL} the metadata keeps the V1 resource, forced to https for a TLS proxy. */
@QuarkusTest
class ResourceMetadataDefaultTest {

    @Test
    void resourceStaysTheHttpsServerRoot() throws Exception {
        JsonNode document = ResourceMetadataTest
                .getJson(URI.create("http://localhost:8081/.well-known/oauth-protected-resource"));

        assertEquals("https://localhost:8081", document.path("resource").asText(), document.toString());
    }
}
