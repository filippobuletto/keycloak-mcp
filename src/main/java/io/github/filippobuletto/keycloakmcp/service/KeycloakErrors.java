package io.github.filippobuletto.keycloakmcp.service;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ToolCallException;

/**
 * Translates Keycloak admin API failures into failed tool responses: HTTP errors carry the status and Keycloak's own
 * message, connection failures say Keycloak is unreachable.
 */
public final class KeycloakErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private KeycloakErrors() {
    }

    public static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (WebApplicationException e) {
            throw failure(e.getResponse());
        } catch (ProcessingException e) {
            throw new ToolCallException("Keycloak is unreachable: " + ioFailure(e).getMessage());
        }
    }

    /** The outermost I/O exception names the host; deeper causes often repeat the error without it. */
    private static Throwable ioFailure(Throwable e) {
        Throwable cause = e;
        while (!(cause instanceof IOException) && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }

    /** For admin client calls that return a raw {@link Response} instead of throwing, such as deletes. */
    public static void checked(Supplier<Response> action) {
        try (Response response = call(action)) {
            requireSuccess(response);
        }
    }

    /** For create calls; returns the id Keycloak puts in the {@code Location} header. */
    public static String createdId(Supplier<Response> action) {
        try (Response response = call(action)) {
            requireSuccess(response);
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private static void requireSuccess(Response response) {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw failure(response);
        }
    }

    private static ToolCallException failure(Response response) {
        return new ToolCallException(
                message(response.getStatus(), response.getStatusInfo().getReasonPhrase(), body(response)));
    }

    static String message(int status, String reason, String body) {
        String prefix = "Keycloak returned " + status + " " + reason;
        String detail = detail(body);
        return detail == null ? prefix : prefix + ": " + detail;
    }

    /** A failed token exchange; {@code body} is the issuer's error response, which carries no token. */
    static ToolCallException exchangeFailure(String body) {
        String detail = body;
        try {
            JsonNode json = JSON.readTree(body);
            if (json.path("error").isTextual()) {
                detail = json.get("error").asText();
                if (json.path("error_description").isTextual()) {
                    detail += ": " + json.get("error_description").asText();
                }
            }
        } catch (Exception e) {
            // not JSON, such as "OIDC Server is not available": keep it as it is
        }
        return new ToolCallException("Token exchange at the issuer failed: " + detail);
    }

    private static String detail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode json = JSON.readTree(body);
            for (String field : new String[] { "errorMessage", "error" }) {
                JsonNode value = json.get(field);
                if (value != null && value.isTextual()) {
                    return value.asText();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String body(Response response) {
        try {
            return response.readEntity(String.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
