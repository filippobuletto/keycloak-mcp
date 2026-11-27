package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/**
 * A top-level flow. {@code type} is {@code basic-flow} or {@code client-flow}. {@code usedBy} names each binding that
 * runs the flow, such as {@code realm: BROWSER}, {@code client kiosk: BROWSER} or
 * {@code identity provider corp: FIRST_BROKER_LOGIN}.
 */
public record AuthenticationFlowSummary(
        String alias,
        String description,
        String type,
        boolean builtIn,
        List<String> usedBy) {
}
