package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;
import org.keycloak.representations.idm.ProtocolMapperTypeRepresentation;

import io.quarkiverse.mcp.server.ToolCallException;

class ClientScopeMapperServiceTest {

    static ProtocolMapperTypeRepresentation type(String... keys) {
        ProtocolMapperTypeRepresentation type = new ProtocolMapperTypeRepresentation();
        type.setId("oidc-hardcoded-claim-mapper");
        type.setProperties(List.of(keys).stream().map(key -> {
            ConfigPropertyRepresentation property = new ConfigPropertyRepresentation();
            property.setName(key);
            return property;
        }).toList());
        return type;
    }

    @Test
    void acceptsDeclaredKeys() {
        ClientScopeMapperService.checkConfig(type("claim.name", "claim.value"), Set.of("claim.value"));
        ClientScopeMapperService.checkConfig(type("claim.name"), Set.of());
    }

    @Test
    void refusesAMissingRequiredKey() {
        ProtocolMapperTypeRepresentation type = type("claim.name", "claim.value");
        type.getProperties().getFirst().setRequired(true);
        ClientScopeMapperService.checkRequired(type, Set.of("claim.name"));

        ToolCallException e = assertThrows(ToolCallException.class,
                () -> ClientScopeMapperService.checkRequired(type, Set.of("claim.value")));

        assertEquals("Config key claim.name is required by mapper type oidc-hardcoded-claim-mapper."
                + " Nothing was changed.", e.getMessage());
    }

    @Test
    void refusesAnUndeclaredKeyNamingTheValidOnes() {
        ToolCallException e = assertThrows(ToolCallException.class, () -> ClientScopeMapperService
                .checkConfig(type("claim.value", "claim.name"), Set.of("claim.name", "claim.nmae")));

        assertEquals("Config key claim.nmae is not declared by mapper type oidc-hardcoded-claim-mapper;"
                + " valid keys: claim.name, claim.value. Nothing was changed.", e.getMessage());
    }
}
