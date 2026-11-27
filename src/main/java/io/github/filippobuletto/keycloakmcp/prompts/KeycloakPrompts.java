package io.github.filippobuletto.keycloakmcp.prompts;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.github.filippobuletto.keycloakmcp.service.RealmService;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;

/**
 * Instructions for common Keycloak jobs. Getting a prompt makes no Keycloak call; the agent calls the tools. Texts put
 * tool names, and nothing else, in backticks, and never ask the agent to skip a confirmation.
 */
@Singleton
public class KeycloakPrompts {

    @Inject
    RealmService realms;

    @Prompt(name = "audit-realm", description = "Review a realm's security settings, password policy and brute-force protection included, and list what to change. Reads only.")
    PromptMessage auditRealm(@PromptArg(description = "Realm name") String realm) {
        return PromptMessage.withUserRole("""
                Audit the security settings of the Keycloak realm %s. Read only: change nothing.

                1. Read the realm with `getRealm`.
                2. Read its clients with `listClients`, and `getClient` for each client that needs a closer look.
                3. Read `listRequiredActions`, `listAuthenticationFlows`, `listIdentityProviders` and \
                `getEventSettings`.

                Check at least:
                - SSL required, user registration and the email settings.
                - Token and session lifespans.
                - The password policy: minimum length, character classes, not username, not email, password \
                history, hashing iterations and a password blacklist. A realm with no password policy is a finding.
                - The brute-force protection: whether it is on, the failure factor, the wait times, temporary \
                and permanent lockout.
                - Public clients, wildcard redirect URIs or web origins, and direct access grants.
                - Required actions that are disabled or not default, such as CONFIGURE_TOTP.
                - Whether login events and admin events are recorded.

                Report the findings grouped by severity (high, medium, low). For each finding give the setting, \
                its current value, the risk and the fix.
                """.formatted(realm));
    }

    @Prompt(name = "troubleshoot-login", description = "Find out why a user can't sign in to a realm. Reads only unless the operator approves a change.")
    PromptMessage troubleshootLogin(@PromptArg(description = "Realm name") String realm,
            @PromptArg(description = "Username of the user who can't sign in") String username) {
        return PromptMessage.withUserRole("""
                Find out why the user %2$s can't sign in to the Keycloak realm %1$s.

                1. Find the user with `searchUsers` and note the user id.
                2. Read the user with `getUser`: enabled, email verified, pending required actions.
                3. Read the user's sessions with `listUserSessions`.
                4. Read recent LOGIN_ERROR events for the user id with `listEvents`. If events are not recorded, \
                say so.
                5. Read the realm's brute-force protection with `getRealm`, and the browser and direct grant \
                flows with `listAuthenticationFlows`.

                Report the likely cause and the fix. Change nothing without asking the operator first.
                """.formatted(realm, username));
    }

    @Prompt(name = "setup-client", description = "Walk the operator through creating an OIDC client in a realm.")
    PromptMessage setupClient(@PromptArg(description = "Realm name") String realm,
            @PromptArg(description = "Client id of the new client") String clientId) {
        return PromptMessage.withUserRole("""
                Help me create the OIDC client %2$s in the Keycloak realm %1$s.

                1. Check with `listClients` that no client %2$s exists yet. If one does, stop and tell me.
                2. Ask me whether the client is public or confidential, and for its redirect URIs and web origins.
                3. Create the client with `createClient`.
                4. If I ask for extra client scopes, add them with `addClientScopeToClient`; if I ask for mappers, \
                add them with `addClientScopeMapper`.
                5. Show the result with `getClient`.
                """.formatted(realm, clientId));
    }

    @CompletePrompt("audit-realm")
    List<String> completeAuditRealm(String realm) {
        return realms.namesStartingWith(realm);
    }

    @CompletePrompt("troubleshoot-login")
    List<String> completeTroubleshootRealm(String realm) {
        return realms.namesStartingWith(realm);
    }

    @CompletePrompt("setup-client")
    List<String> completeSetupRealm(String realm) {
        return realms.namesStartingWith(realm);
    }
}
