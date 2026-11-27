package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

public record ClientClientScopes(
        String clientId,
        List<String> defaultScopes,
        List<String> optionalScopes) {
}
