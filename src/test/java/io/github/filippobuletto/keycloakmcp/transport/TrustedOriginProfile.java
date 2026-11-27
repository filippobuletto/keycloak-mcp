package io.github.filippobuletto.keycloakmcp.transport;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Trusts one browser origin. Tests bind localhost, where the MCP extension's DNS-rebinding check rejects every
 * non-localhost {@code Origin} before CORS sees it, so this profile turns that check off.
 */
public class TrustedOriginProfile implements QuarkusTestProfile {

    public static final String TRUSTED = "https://trusted.example";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.http.cors.origins", TRUSTED,
                "quarkus.mcp.server.http.dns-rebinding-check.enabled", "false");
    }
}
