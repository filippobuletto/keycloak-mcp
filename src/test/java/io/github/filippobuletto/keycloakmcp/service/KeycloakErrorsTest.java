package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ConnectException;

import jakarta.ws.rs.ProcessingException;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ToolCallException;

class KeycloakErrorsTest {

    @Test
    void prefersErrorMessageField() {
        assertEquals("Keycloak returned 409 Conflict: User exists with same username",
                KeycloakErrors.message(409, "Conflict", "{\"errorMessage\":\"User exists with same username\"}"));
    }

    @Test
    void fallsBackToErrorField() {
        assertEquals("Keycloak returned 404 Not Found: Realm not found.",
                KeycloakErrors.message(404, "Not Found", "{\"error\":\"Realm not found.\"}"));
    }

    @Test
    void usesStatusOnlyWhenBodyIsNotJson() {
        assertEquals("Keycloak returned 502 Bad Gateway",
                KeycloakErrors.message(502, "Bad Gateway", "<html>proxy error</html>"));
    }

    @Test
    void usesStatusOnlyWhenBodyIsEmpty() {
        assertEquals("Keycloak returned 403 Forbidden", KeycloakErrors.message(403, "Forbidden", ""));
    }

    @Test
    void exchangeFailureQuotesErrorAndDescription() {
        assertEquals("Token exchange at the issuer failed: access_denied: Client is not within the token audience",
                KeycloakErrors.exchangeFailure(
                        "{\"error\":\"access_denied\",\"error_description\":\"Client is not within the token audience\"}")
                        .getMessage());
    }

    @Test
    void exchangeFailureWithoutDescription() {
        assertEquals("Token exchange at the issuer failed: invalid_client",
                KeycloakErrors.exchangeFailure("{\"error\":\"invalid_client\"}").getMessage());
    }

    @Test
    void exchangeFailureKeepsAMessageThatIsNotJson() {
        assertEquals("Token exchange at the issuer failed: OIDC Server is not available",
                KeycloakErrors.exchangeFailure("OIDC Server is not available").getMessage());
    }

    @Test
    void reportsUnreachableKeycloak() {
        ToolCallException e = assertThrows(ToolCallException.class, () -> KeycloakErrors.call(() -> {
            throw new ProcessingException("io failure",
                    new ConnectException("Connection refused: localhost/127.0.0.1:8080"));
        }));

        assertEquals("Keycloak is unreachable: Connection refused: localhost/127.0.0.1:8080", e.getMessage());
    }

    @Test
    void reportsTheIoFailureThatNamesTheHost() {
        ConnectException withHost = new ConnectException("Connection refused (connect failed): localhost/127.0.0.1:8080");
        withHost.initCause(new ConnectException("Connection refused (connect failed)"));

        ToolCallException e = assertThrows(ToolCallException.class, () -> KeycloakErrors.call(() -> {
            throw new ProcessingException(new RuntimeException("wrapper", withHost));
        }));

        assertEquals("Keycloak is unreachable: Connection refused (connect failed): localhost/127.0.0.1:8080",
                e.getMessage());
    }
}
