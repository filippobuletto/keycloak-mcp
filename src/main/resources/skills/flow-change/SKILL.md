---
name: flow-change
description: Steps for changing a Keycloak authentication flow without locking users out - copy the flow, change the copy, review it, and bind it only after the operator agrees, with the way back stated. Use for any change to sign-in steps such as OTP, passkeys, identity provider redirects or registration.
license: EUPL-1.2
---

# Changing an authentication flow

A flow change takes effect for every sign-in of that kind as soon as the
flow is bound. A mistake can lock out every user, administrators included.
Follow these steps in order, and follow the `safe-changes` skill for each
call that changes something.

## 1. Read what is bound now

Call `listAuthenticationFlows`. For each flow it shows what uses it: realm
slots (BROWSER, DIRECT_GRANT, ...), client overrides and identity providers.
Note the flow bound to the slot you will change; it is the way back.

Read that flow with `getAuthenticationFlow`. It lists every step with its
execution id, level, requirement and whether it takes config.

## 2. Never edit a built-in flow in place

Built-in flows (browser, direct grant, registration, reset credentials,
first broker login, ...) are refused by the execution tools. Copy the flow
with `copyAuthenticationFlow` under a new alias that says what it is for,
for example "browser-admin-otp". A copy affects no sign-in until it is
bound, so you can change it freely.

Keycloak renames the copied sub-flows (it puts the new alias in front of
their names). Read the copy with `getAuthenticationFlow` and use the
sub-flow aliases it shows, not the original ones.

## 3. Change the copy

- `listAuthenticatorTypes` lists the step types; with a providerId it shows
  the config keys that type takes.
- `addFlowExecution` adds a step and `addFlowSubFlow` a sub-flow, at the end
  of the flow or of a sub-flow. Both start DISABLED.
- `updateFlowExecution` sets the requirement: REQUIRED, ALTERNATIVE,
  CONDITIONAL or DISABLED, among those the step allows.
- `moveFlowExecution` moves a step one place up or down.
- `setExecutionConfig` sets a step's config, for example the role of a
  role condition.
- `deleteFlowExecution` removes a step; it asks first.

Recipes for common changes are in
[references/recipes.md](references/recipes.md).

## 4. Review the copy with the operator

Show the changed flow with `getAuthenticationFlow` and explain, step by
step, what a user will now see at sign-in. Point out who could be locked out:
users without the new credential, service users signing in with a password,
or users of an identity provider.

## 5. Bind only after the operator agrees

Before binding, tell the operator:

- the slot and the flow bound to it now, which is the way back,
- to keep an admin session open in another browser while testing, and
- to test the sign-in in a private window right after the change.

Bind with `bindRealmFlow` (the whole realm) or `setClientFlowOverride` (one
client, which is safer to try first). `bindRealmFlow` asks first and returns
the alias it replaced; report it.

## 6. Way back

When the test fails, bind the old flow again with `bindRealmFlow`, or clear
the client override with `setClientFlowOverride` without a flow alias. Leave
the copy in place so it can be fixed; delete it with
`deleteAuthenticationFlow` only when the operator asks, and only when
`listAuthenticationFlows` shows that nothing uses it.

This server refuses flow changes in the `master` realm.
