package io.github.filippobuletto.keycloakmcp.guard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;

/**
 * In {@link ConfirmMode#FLAG}, rejects a destructive tool call unless its {@code confirm} argument is the JSON boolean
 * {@code true}; the tool method would also accept the string {@code "true"}. Runs before the tool method, so a
 * rejected call never reaches Keycloak. In {@link ConfirmMode#ELICIT} it lets every call through to the tool's
 * {@link Confirmation}.
 */
@ApplicationScoped
public class RequireConfirmFlag implements ToolInputGuardrail {

    @Inject
    Confirmation confirmation;

    @Override
    public void apply(ToolInputContext context) {
        if (confirmation.mode() == ConfirmMode.FLAG && !Boolean.TRUE.equals(context.getArguments().getValue("confirm"))) {
            throw new ToolCallException(context.getTool().name()
                    + " is destructive and runs only with confirm=true. Nothing was changed.");
        }
    }
}
