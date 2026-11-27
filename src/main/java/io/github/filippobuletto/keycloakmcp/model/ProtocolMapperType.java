package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/** A protocol mapper type; {@code properties} are the config keys a mapper of this type takes. */
public record ProtocolMapperType(
        String id,
        String name,
        String category,
        String helpText,
        List<Property> properties) {

    /** {@code type} is Keycloak's property type, for example {@code String}, {@code boolean} or {@code List}. */
    public record Property(
            String key,
            String label,
            String type,
            boolean required,
            Object defaultValue,
            List<String> options,
            String helpText) {
    }
}
