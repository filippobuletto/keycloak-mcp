package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ResourceMetadataChallengeTest {

    @Test
    void metadataUrlInsertsTheWellKnownSegmentBeforeTheResourcePath() {
        assertEquals("http://localhost:8091/.well-known/oauth-protected-resource/mcp",
                ResourceMetadataChallenge.metadataUrl(URI.create("http://localhost:8091/mcp")));
    }

    @Test
    void metadataUrlForARootResourceHasNoSuffix() {
        assertEquals("https://mcp.corp.example/.well-known/oauth-protected-resource",
                ResourceMetadataChallenge.metadataUrl(URI.create("https://mcp.corp.example/")));
    }

    @Test
    void challengeKeepsEveryOtherParameter() {
        assertEquals("Bearer error=\"insufficient_scope\", resource_metadata=\"http://x/.well-known/a\", scope=\"s\"",
                ResourceMetadataChallenge.rewrite(
                        "Bearer error=\"insufficient_scope\", resource_metadata=\"https://y/old\", scope=\"s\"",
                        "http://x/.well-known/a"));
    }
}
