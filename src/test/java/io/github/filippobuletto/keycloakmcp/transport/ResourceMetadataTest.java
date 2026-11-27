package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Protected resource metadata as an MCP client discovers it, with {@code MCP_PUBLIC_URL} set. */
@QuarkusTest
@TestProfile(PublicUrlProfile.class)
class ResourceMetadataTest {

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();
    static final Pattern RESOURCE_METADATA = Pattern.compile("resource_metadata=\"([^\"]+)\"");

    @Test
    void challengePointsAtMetadataForThePublicUrl() throws Exception {
        URI metadata = challengedMetadataUrl(URI.create(PublicUrlProfile.PUBLIC_URL));

        JsonNode document = getJson(metadata);

        assertEquals(PublicUrlProfile.PUBLIC_URL, document.path("resource").asText(), document.toString());
    }

    @Test
    void metadataIsServedAtTheRfc9728PathForMcp() throws Exception {
        JsonNode document = getJson(URI.create("http://localhost:8081/.well-known/oauth-protected-resource/mcp"));

        assertEquals(PublicUrlProfile.PUBLIC_URL, document.path("resource").asText(), document.toString());
    }

    /** Sends an unauthenticated MCP request and returns the {@code resource_metadata} URL from the 401 challenge. */
    static URI challengedMetadataUrl(URI mcp) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(mcp)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(), response.body());
        String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
        Matcher matcher = RESOURCE_METADATA.matcher(challenge);
        assertTrue(matcher.find(), challenge);
        return URI.create(matcher.group(1));
    }

    static JsonNode getJson(URI uri) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), uri + " -> " + response.body());
        return JSON.readTree(response.body());
    }
}
