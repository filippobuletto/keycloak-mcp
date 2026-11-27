package io.github.filippobuletto.keycloakmcp.guard;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.BooleanSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.ToolCallException;
import io.smallrye.mutiny.TimeoutException;

/**
 * Gets the confirmation a destructive tool needs before it changes anything. In {@link ConfirmMode#ELICIT} the server
 * asks the user through a form elicitation with one required checkbox; in {@link ConfirmMode#FLAG} the call needs
 * {@code confirm=true}. Every refusal is a {@link ToolCallException} that ends in "Nothing was changed.".
 */
@ApplicationScoped
public class Confirmation {

    /** Tool description sentence for a tool that needs confirmation. */
    public static final String ASKS = "Asks the user to confirm (with MCP_CONFIRM_MODE=flag: requires confirm=true).";

    /** {@link #ASKS} for the middle of a sentence. */
    public static final String ASKS_LOWER = "asks the user to confirm (with MCP_CONFIRM_MODE=flag: requires"
            + " confirm=true).";

    /** Description of the {@code confirm} argument. */
    public static final String FLAG_ARG = "Used only when the server runs with MCP_CONFIRM_MODE=flag: must be true to"
            + " proceed. Otherwise the server asks the user.";

    static final String FIELD = "confirm";

    private final ConfirmMode mode;
    private final Duration timeout;

    Confirmation(@ConfigProperty(name = "keycloak-mcp.confirm.mode") ConfirmMode mode,
            @ConfigProperty(name = "quarkus.mcp.server.elicitation.default-timeout", defaultValue = "60s") Duration timeout) {
        this.mode = mode;
        this.timeout = timeout;
    }

    /** The configured mode. */
    public ConfirmMode mode() {
        return mode;
    }

    /**
     * Returns only if the call may proceed.
     *
     * @param tool the tool name, for the refusal messages
     * @param elicitation the current request's elicitation
     * @param confirm the call's {@code confirm} argument, used only in flag mode
     * @param question the dialog message: action, target, realm and consequence
     */
    public void require(String tool, Elicitation elicitation, boolean confirm, String question) {
        require(tool, elicitation, confirm, question, tool + " is destructive and runs only with confirm=true.");
    }

    /**
     * As {@link #require(String, Elicitation, boolean, String)}, with the refusal for a call without
     * {@code confirm=true} in flag mode.
     */
    public void require(String tool, Elicitation elicitation, boolean confirm, String question, String flagRefusal) {
        if (mode == ConfirmMode.FLAG) {
            if (!confirm) {
                throw refused(flagRefusal);
            }
            return;
        }
        if (!elicitation.isFormModeSupported()) {
            throw refused(tool + " needs the user's confirmation, but this client does not support MCP elicitation"
                    + " (form mode). Use a client that does, or run the server with MCP_CONFIRM_MODE=flag.");
        }
        ElicitationRequest request = elicitation.requestBuilder()
                .setMessage(question)
                .addSchemaProperty(FIELD, BooleanSchema.builder()
                        .setTitle("I understand, proceed")
                        .setRequired(true)
                        .build())
                .build();
        if (!elicitation.isServerInitiatedRequestSupported()) {
            // Stateless protocol: return the dialog as input_required; the client retries with the answer
            ElicitationResponse answer = elicitation.inputResponses().getElicitationResponse(FIELD);
            if (answer == null) {
                throw elicitation.inputRequired().addElicitationRequest(FIELD, request).build();
            }
            check(tool, answer);
            return;
        }
        ElicitationResponse response;
        try {
            response = request.sendAndAwait();
        } catch (TimeoutException e) {
            throw refused(tool + " was not confirmed: no answer within " + timeout.toSeconds() + " s.");
        }
        check(tool, response);
    }

    private static void check(String tool, ElicitationResponse response) {
        switch (response.action()) {
            case ACCEPT -> {
                if (!Boolean.TRUE.equals(response.content().getBoolean(FIELD))) {
                    throw refused(tool + " was not confirmed: the user declined.");
                }
            }
            case DECLINE -> throw refused(tool + " was not confirmed: the user declined.");
            case CANCEL -> throw refused(tool + " was not confirmed: the user dismissed the dialog.");
        }
    }

    private static ToolCallException refused(String reason) {
        return new ToolCallException(reason + " Nothing was changed.");
    }
}
