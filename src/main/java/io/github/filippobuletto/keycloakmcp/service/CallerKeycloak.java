package io.github.filippobuletto.keycloakmcp.service;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClientException;
import io.quarkus.oidc.common.runtime.OidcConstants;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Produces the {@link Caller} admin client. Over stdio it is the configured client: the credentials in the environment
 * are the trust boundary. Over HTTP it acts as the signed-in user: once per request the user's bearer token is
 * exchanged at the issuer (RFC 8693, client {@code quarkus.oidc-client}) for a token with the Admin API scope, and
 * Keycloak gets only that token. Nothing falls back to the configured client.
 */
@ApplicationScoped
public class CallerKeycloak {

    @Inject
    Keycloak configured;

    @Inject
    OidcClient exchange;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "quarkus.mcp.server.stdio.enabled")
    boolean stdio;

    @ConfigProperty(name = "keycloak-mcp.keycloak-url")
    String serverUrl;

    @Produces
    @Caller
    @RequestScoped
    Keycloak caller() {
        if (stdio) {
            return configured;
        }
        String subject = identity.getCredential(TokenCredential.class).getToken();
        String token;
        try {
            token = exchange.getTokens(Map.of(OidcConstants.EXCHANGE_GRANT_SUBJECT_TOKEN, subject))
                    .await().indefinitely().getAccessToken();
        } catch (OidcClientException e) {
            throw KeycloakErrors.exchangeFailure(e.getMessage());
        }
        // The realm only names a token endpoint, which a client with a fixed token never calls
        return KeycloakBuilder.builder().serverUrl(serverUrl).realm("master").authorization(token).build();
    }

    void close(@Disposes @Caller Keycloak keycloak) {
        if (keycloak != configured) {
            keycloak.close();
        }
    }
}
