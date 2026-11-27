package io.github.filippobuletto.keycloakmcp.tools;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.Startup;

/**
 * Refuses to start when the client secret tools are on together with logging that records message bodies, because the
 * log would then hold every secret the tools return.
 * <p>
 * shortcut: checks the default MCP server's traffic logging only; check named servers too if the app defines any.
 */
@Startup
@ApplicationScoped
class ClientSecretsLoggingCheck {

    ClientSecretsLoggingCheck(
            @ConfigProperty(name = "keycloak-mcp.client-secrets.enabled") boolean secretsEnabled,
            @ConfigProperty(name = "quarkus.mcp.server.traffic-logging.enabled", defaultValue = "false") boolean trafficLogging,
            @ConfigProperty(name = "quarkus.rest-client.logging.scope") Optional<String> restClientScope) {
        if (!secretsEnabled) {
            return;
        }
        if (trafficLogging) {
            throw refusal("quarkus.mcp.server.traffic-logging.enabled=true");
        }
        if (restClientScope.filter(scope -> !scope.equalsIgnoreCase("none")).isPresent()) {
            throw refusal("quarkus.rest-client.logging.scope=" + restClientScope.get());
        }
    }

    private static IllegalStateException refusal(String setting) {
        return new IllegalStateException("MCP_CLIENT_SECRETS_ENABLED=true cannot run with " + setting
                + ": the log would hold every client secret the tools return. Turn one of them off.");
    }
}
