package io.github.filippobuletto.keycloakmcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;

/**
 * MCP test clients that authenticate like a real HTTP client: a bearer token from the {@code master} issuer
 * realm, obtained with the password grant against Dev Services Keycloak.
 */
public final class McpClients {

    public static final String ADMIN_USER = "mcp-user";
    public static final String GUEST_USER = "mcp-guest";
    /** Holds {@code mcp-admin} and only {@code view-users} and {@code view-realm} of {@code acme-realm}. */
    public static final String VIEWER_USER = "mcp-viewer";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private McpClients() {
    }

    /** Connected client acting as {@code mcp-user}, who holds the {@code mcp-admin} role. */
    public static McpStreamableTestClient connected() {
        return connected(ADMIN_USER);
    }

    /** Connected client acting as {@code username}. */
    public static McpStreamableTestClient connected(String username) {
        return McpAssured.newStreamableClient().setBearerToken(accessToken(username)).build().connect();
    }

    /** Access token for a seeded user of {@code master} (docker/master.json); each user's password equals the username. */
    public static String accessToken(String username) {
        String issuer = ConfigProvider.getConfig().getValue("keycloak.url", String.class) + "/realms/master";
        String form = Map.of("grant_type", "password", "client_id", "keycloak-mcp", "username", username, "password",
                username)
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Token request for " + username + " failed: " + response.statusCode()
                        + " " + response.body());
            }
            return JSON.readTree(response.body()).get("access_token").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
