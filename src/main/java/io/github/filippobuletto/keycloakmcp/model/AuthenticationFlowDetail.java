package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;
import java.util.Map;

/** A top-level flow as in {@link AuthenticationFlowSummary}, with its executions in the order Keycloak runs them. */
public record AuthenticationFlowDetail(
        String alias,
        String description,
        String type,
        boolean builtIn,
        List<String> usedBy,
        List<Execution> executions) {

    /**
     * An authenticator step ({@code providerId} set) or a sub-flow ({@code subFlowAlias} set). {@code level} is the
     * nesting depth, 0 for the top-level flow's own steps. {@code configAlias} and {@code config} are null when the
     * step has no config.
     */
    public record Execution(
            String id,
            int level,
            String displayName,
            String providerId,
            String subFlowAlias,
            String requirement,
            List<String> requirementChoices,
            boolean configurable,
            String configAlias,
            Map<String, String> config) {
    }
}
