package io.github.filippobuletto.keycloakmcp.guard;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;

/**
 * Rejects a call whose {@code realm} argument is {@code master}. The master realm holds the administrators of the
 * whole Keycloak instance, so tools that change it stay out of reach of MCP clients.
 */
public class ProtectMaster implements ToolInputGuardrail {

    @Override
    public void apply(ToolInputContext context) {
        if ("master".equals(context.getArguments().getValue("realm"))) {
            throw new ToolCallException(context.getTool().name()
                    + " is refused for the master realm, which holds the Keycloak administrators. Nothing was changed.");
        }
    }
}
