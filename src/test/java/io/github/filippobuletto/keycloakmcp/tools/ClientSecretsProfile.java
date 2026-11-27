package io.github.filippobuletto.keycloakmcp.tools;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Turns the client secret tools on with {@code MCP_CLIENT_SECRETS_ENABLED=true}. */
public class ClientSecretsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("MCP_CLIENT_SECRETS_ENABLED", "true");
    }
}
