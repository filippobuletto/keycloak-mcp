package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Browser-style requests to {@code /mcp}: McpAssured cannot send {@code Origin} or {@code OPTIONS}. */
@QuarkusTest
@TestProfile(TrustedOriginProfile.class)
class CorsTest {

    static final String UNTRUSTED = "https://evil.example";
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"cors-test","version":"1"}}}""";

    @TestHTTPResource("/mcp")
    URI mcp;

    @Test
    void preflightFromTrustedOriginAllowsMcpRequests() throws Exception {
        HttpResponse<String> response = preflight(TrustedOriginProfile.TRUSTED);

        assertEquals(200, response.statusCode(), response.toString());
        assertEquals(Optional.of(TrustedOriginProfile.TRUSTED),
                response.headers().firstValue("Access-Control-Allow-Origin"));
        assertListed(response, "Access-Control-Allow-Methods", "POST");
        assertListed(response, "Access-Control-Allow-Headers", "Authorization");
        assertListed(response, "Access-Control-Allow-Headers", "Mcp-Session-Id");
        assertNoCredentials(response);
    }

    @Test
    void preflightFromUntrustedOriginIsNotAllowed() throws Exception {
        HttpResponse<String> response = preflight(UNTRUSTED);

        assertEquals(Optional.empty(), response.headers().firstValue("Access-Control-Allow-Origin"),
                response.headers().toString());
    }

    @Test
    void initializeFromUntrustedOriginIsForbidden() throws Exception {
        HttpResponse<String> response = initialize(UNTRUSTED);

        assertEquals(403, response.statusCode(), response.body());
    }

    @Test
    void initializeFromTrustedOriginExposesTheSession() throws Exception {
        HttpResponse<String> response = initialize(TrustedOriginProfile.TRUSTED);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Optional.of(TrustedOriginProfile.TRUSTED),
                response.headers().firstValue("Access-Control-Allow-Origin"));
        assertTrue(response.headers().firstValue("Mcp-Session-Id").isPresent(), response.headers().toString());
        assertListed(response, "Access-Control-Expose-Headers", "Mcp-Session-Id");
        assertNoCredentials(response);
    }

    HttpResponse<String> preflight(String origin) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(mcp)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,mcp-session-id")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> initialize(String origin) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(mcp)
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE))
                .header("Origin", origin)
                .header("Authorization", "Bearer " + McpClients.accessToken(McpClients.ADMIN_USER))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    static void assertListed(HttpResponse<?> response, String header, String expected) {
        String value = response.headers().firstValue(header).orElse("");
        assertTrue(value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)),
                header + " was '" + value + "', expected it to list " + expected);
    }

    static void assertNoCredentials(HttpResponse<?> response) {
        assertNotEquals(Optional.of("true"), response.headers().firstValue("Access-Control-Allow-Credentials"),
                response.headers().toString());
    }
}
