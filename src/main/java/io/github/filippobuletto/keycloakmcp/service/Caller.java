package io.github.filippobuletto.keycloakmcp.service;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/** The Keycloak admin client that acts for whoever sent the current MCP request; see {@link CallerKeycloak}. */
@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER })
public @interface Caller {
}
