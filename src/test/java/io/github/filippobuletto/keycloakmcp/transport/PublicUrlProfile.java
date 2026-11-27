package io.github.filippobuletto.keycloakmcp.transport;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Sets {@code MCP_PUBLIC_URL} to the plain-http MCP endpoint of the test server (default test port 8081). */
public class PublicUrlProfile implements QuarkusTestProfile {

    public static final String PUBLIC_URL = "http://localhost:8081/mcp";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("MCP_PUBLIC_URL", PUBLIC_URL);
    }
}
