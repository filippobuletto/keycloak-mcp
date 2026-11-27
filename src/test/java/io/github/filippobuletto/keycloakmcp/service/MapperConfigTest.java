package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;

import io.quarkiverse.mcp.server.ToolCallException;

class MapperConfigTest {

    static List<ConfigPropertyRepresentation> properties(String... keys) {
        return List.of(keys).stream().map(key -> {
            ConfigPropertyRepresentation property = new ConfigPropertyRepresentation();
            property.setName(key);
            return property;
        }).toList();
    }

    @Test
    void acceptsDeclaredKeys() {
        MapperConfig.check("hardcoded-attribute-idp-mapper", properties("attribute", "attribute.value"),
                Set.of("attribute.value"));
        MapperConfig.check("hardcoded-attribute-idp-mapper", null, Set.of());
    }

    @Test
    void refusesAnUndeclaredKeyNamingTheValidOnes() {
        ToolCallException e = assertThrows(ToolCallException.class,
                () -> MapperConfig.check("hardcoded-attribute-idp-mapper",
                        properties("attribute.value", "attribute"), Set.of("attribute", "atribute")));

        assertEquals("Config key atribute is not declared by mapper type hardcoded-attribute-idp-mapper;"
                + " valid keys: attribute, attribute.value. Nothing was changed.", e.getMessage());
    }
}
