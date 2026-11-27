package io.github.filippobuletto.keycloakmcp.tools;

import java.util.Set;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * Hides the client secret tools from {@code tools/list} and refuses them in {@code tools/call} unless
 * {@code MCP_CLIENT_SECRETS_ENABLED=true}. quarkus-mcp-server treats a filter that throws as passed, so this one only
 * compares names.
 */
@Singleton
class ClientSecretsFilter implements ToolFilter {

    static final Set<String> SECRET_TOOLS = Set.of(ClientTools.GET_CLIENT_SECRET, ClientTools.REGENERATE_CLIENT_SECRET);

    private final boolean enabled;

    ClientSecretsFilter(@ConfigProperty(name = "keycloak-mcp.client-secrets.enabled") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean test(ToolInfo tool, FilterContext context) {
        return enabled || !SECRET_TOOLS.contains(tool.name());
    }
}
