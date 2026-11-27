package io.github.filippobuletto.keycloakmcp.tools;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Confirmations through an MCP elicitation ({@code MCP_CONFIRM_MODE=elicit}), with a short dialog timeout and the
 * client secret tools on, so every confirmation can be tested.
 */
public class ElicitProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("MCP_CONFIRM_MODE", "elicit",
                "quarkus.mcp.server.elicitation.default-timeout", "2s",
                "MCP_CLIENT_SECRETS_ENABLED", "true");
    }
}
