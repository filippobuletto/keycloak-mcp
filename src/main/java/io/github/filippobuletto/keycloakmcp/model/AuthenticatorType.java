package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/**
 * An authenticator type: {@code kind} is {@code authenticator}, {@code form}, {@code form-action} or
 * {@code client-authenticator}. {@code properties} are the config keys a step of this type takes; they are listed only
 * when one type is asked for, and null otherwise.
 */
public record AuthenticatorType(
        String id,
        String name,
        String description,
        String kind,
        List<ProtocolMapperType.Property> properties) {
}
