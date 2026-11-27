package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.filippobuletto.keycloakmcp.model.ClientInput;

class ClientServiceTest {

    @Test
    void dropsSecretAttributes() {
        Map<String, String> attributes = Map.of(
                "pkce.code.challenge.method", "S256",
                "client.secret.creation.time", "1700000000",
                "client.secret.rotated", "old-secret-value",
                "Client.Secret.Expiration", "0");

        assertEquals(Map.of("pkce.code.challenge.method", "S256"), ClientService.safeAttributes(attributes));
    }

    @Test
    void keepsOnlyKnownSettings() {
        Map<String, String> attributes = Map.of(
                "backchannel.logout.url", "https://app.example/logout",
                "post.logout.redirect.uris", "https://app.example/*",
                "saml.signing.private.key", "MIIEvQIBADANBg",
                "vendor.extension.token", "opaque");

        assertEquals(Map.of("backchannel.logout.url", "https://app.example/logout",
                "post.logout.redirect.uris", "https://app.example/*"), ClientService.safeAttributes(attributes));
    }

    @Test
    void keepsSamlSignatureAndEncryptionSettingsButNoKeys() {
        Map<String, String> attributes = Map.of(
                "saml.assertion.signature", "true",
                "saml.client.signature", "true",
                "saml.encrypt", "false",
                "saml.onetimeuse.condition", "true",
                "saml.signature.algorithm", "RSA_SHA256",
                "saml.signing.certificate", "MIIEvTCCAqUCBgGi",
                "saml.encryption.certificate", "MIIEvTCCAqUCBgGj");

        assertEquals(Map.of(
                "saml.assertion.signature", "true",
                "saml.client.signature", "true",
                "saml.encrypt", "false",
                "saml.onetimeuse.condition", "true",
                "saml.signature.algorithm", "RSA_SHA256"), ClientService.safeAttributes(attributes));
    }

    @Test
    void handlesMissingAttributes() {
        assertEquals(Map.of(), ClientService.safeAttributes(null));
    }

    @Test
    void applyChangesOnlySuppliedFields() {
        ClientRepresentation client = new ClientRepresentation();
        client.setName("Billing");
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setRedirectUris(List.of("https://old/*"));
        client.setWebOrigins(List.of("https://old"));

        ClientService.apply(client, new ClientInput(null, null, null, null, null, List.of("https://new/*"), null));

        assertEquals("Billing", client.getName());
        assertFalse(client.isPublicClient());
        assertTrue(client.isServiceAccountsEnabled());
        assertEquals(List.of("https://new/*"), client.getRedirectUris());
        assertEquals(List.of("https://old"), client.getWebOrigins());
    }

    @Test
    void applySetsAllSuppliedFields() {
        ClientRepresentation client = new ClientRepresentation();

        ClientService.apply(client,
                new ClientInput("Web", true, false, true, false, List.of("http://a/*"), List.of("http://a")));

        assertEquals("Web", client.getName());
        assertTrue(client.isPublicClient());
        assertFalse(client.isServiceAccountsEnabled());
        assertTrue(client.isStandardFlowEnabled());
        assertFalse(client.isDirectAccessGrantsEnabled());
        assertEquals(List.of("http://a/*"), client.getRedirectUris());
        assertEquals(List.of("http://a"), client.getWebOrigins());
    }

    @Test
    void serviceClientGetsNoUserLoginFlowsByDefault() {
        ClientRepresentation client = ClientService.newClient("billing-api",
                new ClientInput(null, false, true, null, null, null, null));

        assertEquals("billing-api", client.getClientId());
        assertEquals("openid-connect", client.getProtocol());
        assertFalse(client.isStandardFlowEnabled());
        assertFalse(client.isDirectAccessGrantsEnabled());
    }

    @Test
    void serviceClientKeepsExplicitFlows() {
        ClientRepresentation client = ClientService.newClient("billing-api",
                new ClientInput(null, null, true, true, null, null, null));

        assertTrue(client.isStandardFlowEnabled());
        assertFalse(client.isDirectAccessGrantsEnabled());
    }

    @Test
    void otherClientsKeepKeycloakDefaults() {
        ClientRepresentation web = ClientService.newClient("web", new ClientInput(null, true, null, null, null, null, null));
        ClientRepresentation app = ClientService.newClient("app", new ClientInput(null, null, null, null, null, null, null));

        assertNull(web.isStandardFlowEnabled());
        assertNull(web.isDirectAccessGrantsEnabled());
        assertNull(app.isStandardFlowEnabled());
        assertNull(app.isDirectAccessGrantsEnabled());
    }
}
