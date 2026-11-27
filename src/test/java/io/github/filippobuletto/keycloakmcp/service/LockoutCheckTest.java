package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;

class LockoutCheckTest {

    static AuthenticationExecutionInfoRepresentation step(int level, String providerId, String requirement) {
        AuthenticationExecutionInfoRepresentation step = new AuthenticationExecutionInfoRepresentation();
        step.setLevel(level);
        step.setProviderId(providerId);
        step.setRequirement(requirement);
        step.setAuthenticationFlow(false);
        return step;
    }

    static AuthenticationExecutionInfoRepresentation subFlow(int level, String alias, String requirement) {
        AuthenticationExecutionInfoRepresentation subFlow = new AuthenticationExecutionInfoRepresentation();
        subFlow.setLevel(level);
        subFlow.setDisplayName(alias);
        subFlow.setRequirement(requirement);
        subFlow.setAuthenticationFlow(true);
        return subFlow;
    }

    /** acme's built-in browser flow: OTP is REQUIRED only inside the CONDITIONAL sub-flow. */
    static final List<AuthenticationExecutionInfoRepresentation> BROWSER = List.of(
            step(0, "auth-cookie", "ALTERNATIVE"),
            step(0, "identity-provider-redirector", "ALTERNATIVE"),
            subFlow(0, "forms", "ALTERNATIVE"),
            step(1, "auth-username-password-form", "REQUIRED"),
            subFlow(1, "Browser - Conditional OTP", "CONDITIONAL"),
            step(2, "conditional-user-configured", "REQUIRED"),
            step(2, "auth-otp-form", "REQUIRED"));

    static List<String> check(Map<String, List<AuthenticationExecutionInfoRepresentation>> flows,
            Map<String, List<String>> uses) {
        return LockoutCheck.requiringFlows("auth-otp-form", flows, uses);
    }

    @Test
    void otpInsideAConditionalSubFlowIsNotNamed() {
        assertEquals(List.of(), check(Map.of("browser", BROWSER), Map.of("browser", List.of("realm: BROWSER"))));
    }

    @Test
    void aRequiredTopLevelStepIsNamedWithItsUsers() {
        List<AuthenticationExecutionInfoRepresentation> flow = List.of(step(0, "auth-cookie", "ALTERNATIVE"),
                step(0, "auth-otp-form", "REQUIRED"));

        assertEquals(List.of("otp-browser (realm: BROWSER, client kiosk: BROWSER)"),
                check(Map.of("otp-browser", flow),
                        Map.of("otp-browser", List.of("realm: BROWSER", "client kiosk: BROWSER"))));
    }

    @Test
    void aRequiredStepInsideAnAlternativeSubFlowIsNamed() {
        List<AuthenticationExecutionInfoRepresentation> flow = List.of(subFlow(0, "forms", "ALTERNATIVE"),
                step(1, "auth-username-password-form", "REQUIRED"), step(1, "auth-otp-form", "REQUIRED"));

        assertEquals(List.of("otp-forms (realm: BROWSER)"),
                check(Map.of("otp-forms", flow), Map.of("otp-forms", List.of("realm: BROWSER"))));
    }

    @Test
    void aStepUnderADisabledSubFlowIsNotNamedButALaterSiblingSubFlowCounts() {
        List<AuthenticationExecutionInfoRepresentation> flow = List.of(subFlow(0, "off", "DISABLED"),
                step(1, "auth-otp-form", "REQUIRED"), subFlow(0, "on", "REQUIRED"),
                step(1, "auth-otp-form", "REQUIRED"));

        assertEquals(List.of("mixed (realm: BROWSER)"),
                check(Map.of("mixed", flow), Map.of("mixed", List.of("realm: BROWSER"))));
        assertEquals(List.of(), check(Map.of("mixed", flow.subList(0, 2)), Map.of("mixed", List.of("realm: BROWSER"))));
    }

    @Test
    void anAlternativeOrDisabledStepIsNotNamed() {
        List<AuthenticationExecutionInfoRepresentation> flow = List.of(step(0, "auth-otp-form", "ALTERNATIVE"),
                step(0, "auth-otp-form", "DISABLED"));

        assertEquals(List.of(), check(Map.of("soft", flow), Map.of("soft", List.of("realm: BROWSER"))));
    }

    @Test
    void aFlowNotInUseIsNotNamedAndFlowsComeInAliasOrder() {
        List<AuthenticationExecutionInfoRepresentation> otp = List.of(step(0, "auth-otp-form", "REQUIRED"));

        assertEquals(List.of("a (realm: DIRECT_GRANT)", "b (realm: BROWSER)"),
                check(Map.of("b", otp, "a", otp, "unused", otp),
                        Map.of("b", List.of("realm: BROWSER"), "a", List.of("realm: DIRECT_GRANT"))));
    }
}
