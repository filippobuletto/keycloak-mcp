package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;

/**
 * Finds the flows a user without a credential could not get through once its setup action is disabled: Keycloak
 * 26.8.2 throws {@code CREDENTIAL_SETUP_REQUIRED} when a {@code REQUIRED} step finds no credential and the setup
 * action is off ({@code DefaultAuthenticationFlow}, {@code Authenticator.areRequiredActionsEnabled}).
 */
final class LockoutCheck {

    /** The authenticator step each setup action serves, by required action provider id. */
    static final Map<String, String> AUTHENTICATOR_BY_ACTION = Map.of(
            "CONFIGURE_TOTP", "auth-otp-form",
            "webauthn-register", "webauthn-authenticator",
            "webauthn-register-passwordless", "webauthn-authenticator-passwordless");

    private LockoutCheck() {
    }

    /**
     * The flows in use, in alias order, that have a {@code REQUIRED} step of {@code authenticatorId} with no enclosing
     * sub-flow that is {@code CONDITIONAL} (it runs only for users who already have the credential, as in the built-in
     * browser flow) or {@code DISABLED}. Each entry reads {@code alias (user, user)}.
     *
     * @param executions each flow's executions in Keycloak's order, with levels
     * @param uses who uses each flow, by alias; flows missing here are not in use
     */
    static List<String> requiringFlows(String authenticatorId,
            Map<String, List<AuthenticationExecutionInfoRepresentation>> executions, Map<String, List<String>> uses) {
        List<String> flows = new ArrayList<>();
        new TreeMap<>(uses).forEach((alias, users) -> {
            if (requires(authenticatorId, executions.getOrDefault(alias, List.of()))) {
                flows.add(alias + " (" + String.join(", ", users) + ")");
            }
        });
        return flows;
    }

    private static boolean requires(String authenticatorId, List<AuthenticationExecutionInfoRepresentation> flow) {
        // In Keycloak's order, the last sub-flow seen at each level above a step is that step's parent
        Map<Integer, String> subFlowRequirement = new HashMap<>();
        for (AuthenticationExecutionInfoRepresentation step : flow) {
            boolean skipped = false;
            for (int level = 0; level < step.getLevel(); level++) {
                String requirement = subFlowRequirement.get(level);
                skipped |= "CONDITIONAL".equals(requirement) || "DISABLED".equals(requirement);
            }
            if (Boolean.TRUE.equals(step.getAuthenticationFlow())) {
                subFlowRequirement.put(step.getLevel(), step.getRequirement());
            } else if (!skipped && authenticatorId.equals(step.getProviderId())
                    && "REQUIRED".equals(step.getRequirement())) {
                return true;
            }
        }
        return false;
    }
}
