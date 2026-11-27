package io.github.filippobuletto.keycloakmcp.model;

public record RealmDetail(
        String id,
        String name,
        String displayName,
        boolean enabled,
        Login login,
        Tokens tokens,
        String passwordPolicy,
        BruteForce bruteForce,
        Long adminEventsExpiration,
        String unmanagedAttributePolicy) {

    public record Login(
            boolean registrationAllowed,
            boolean registrationEmailAsUsername,
            boolean resetPasswordAllowed,
            boolean rememberMe,
            boolean verifyEmail,
            boolean loginWithEmailAllowed,
            boolean duplicateEmailsAllowed,
            String sslRequired) {
    }

    /** Lifespans and timeouts in seconds; 0 means unset where Keycloak falls back to another setting. */
    public record Tokens(
            Integer accessTokenLifespan,
            Integer accessCodeLifespan,
            Integer ssoSessionIdleTimeout,
            Integer ssoSessionMaxLifespan,
            Integer offlineSessionIdleTimeout,
            boolean revokeRefreshToken,
            Integer refreshTokenMaxReuse,
            Integer clientSessionIdleTimeout,
            Integer clientSessionMaxLifespan,
            boolean offlineSessionMaxLifespanEnabled,
            Integer offlineSessionMaxLifespan) {
    }

    /** Lockout after failed sign-ins; {@code failureFactor} failures trigger a lockout. */
    public record BruteForce(
            boolean enabled,
            String strategy,
            boolean permanentLockout,
            Integer maxTemporaryLockouts,
            Integer failureFactor,
            Integer waitIncrementSeconds,
            Integer maxFailureWaitSeconds,
            Integer maxDeltaTimeSeconds,
            Integer minimumQuickLoginWaitSeconds,
            Long quickLoginCheckMilliSeconds) {
    }
}
