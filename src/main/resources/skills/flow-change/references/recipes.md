# Flow recipes

Each recipe starts from a copy (see the `flow-change` skill, step 2). Step
names below are those of Keycloak 26; read the copy with
`getAuthenticationFlow` to find the actual aliases and execution ids.

## Require OTP for one role

Goal: users with a given role must enter a one-time code; other users sign
in as before.

1. Copy the browser flow: `copyAuthenticationFlow` with alias "browser" and a
   new alias such as "browser-role-otp".
2. Add a sub-flow inside the copy's forms sub-flow (the one that holds the
   "Username Password Form" step): `addFlowSubFlow` with type BASIC and an
   alias such as "role-otp".
3. Set that sub-flow to CONDITIONAL with `updateFlowExecution`.
4. Inside it, add the role condition: `addFlowExecution` with providerId
   "conditional-user-role", then set it to REQUIRED. Give it its config with
   `setExecutionConfig`: key "condUserRole" set to the role (a realm role by
   name, a client role as the clientId, a space, then the role name).
5. Inside it, add the OTP step: `addFlowExecution` with providerId
   "auth-otp-form", then set it to REQUIRED.
6. Check with `getRequiredAction` that CONFIGURE_TOTP is enabled, so users
   with the role but without OTP are asked to set it up instead of being
   refused.
7. Review, then bind as the skill describes (a client override first is the
   safer test).

The built-in browser flow also has a conditional 2FA sub-flow that asks for
OTP only when the user has already set it up. Leave it as it is; the new
sub-flow adds the requirement for the role.

## Send users straight to one identity provider

Goal: the login page skips the username form and redirects to one
provider.

1. Copy the browser flow under a new alias.
2. Find the "Identity Provider Redirector" step in the copy (providerId
   "identity-provider-redirector"). It is ALTERNATIVE in the built-in flow.
3. Set its config with `setExecutionConfig`: key "defaultProvider" set to the
   provider's alias (see `listIdentityProviders`). Give the config an alias
   when the step has none yet.
4. Keep the username form ALTERNATIVE so administrators can still sign in
   with a password when the provider is down, unless the operator decides
   otherwise.
5. Review, then bind. A client override (`setClientFlowOverride`) limits the
   redirect to one app.

## Restore the original binding

1. `listAuthenticationFlows` shows which flow is bound to each slot now.
2. Bind the original flow again with `bindRealmFlow`; it was reported when
   the copy was bound. For a client override, call `setClientFlowOverride`
   without a flow alias to clear it.
3. Read `listAuthenticationFlows` again to confirm that the slot shows the
   original flow.
4. Delete the copy with `deleteAuthenticationFlow` only if the operator asks
   and nothing uses it any more.
