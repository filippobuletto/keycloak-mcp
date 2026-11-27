---
name: login-troubleshooting
description: Steps for finding out why a user cannot sign in to a Keycloak realm - the user's state, sessions, recent login errors, lockout settings and the sign-in flows - and mapping the error to a cause and a fix. Use when someone reports a failed or blocked sign-in.
license: EUPL-1.2
---

# Login troubleshooting

Work out why one user cannot sign in to one realm. Read only; change
nothing until the operator agrees to a fix (then follow the `safe-changes`
skill).

You need the realm and the user (username or email). Ask for the client or
app they sign in to, and roughly when it failed, if the report does not say.

## 1. Find the user

Call `searchUsers` with the username or email. When no user matches, the
cause is likely a wrong username, a user in another realm, or a user who
exists only in an identity provider and has not signed in yet; say so and
stop. When several match, ask which one.

## 2. Read the user

Call `getUser` with the id and check:

- `enabled` false: an administrator disabled the user.
- emailVerified false while the realm requires email verification
  (login.verifyEmail in `getRealm`): the user must verify first.
- requiredActions not empty: the user must complete each one (update
  password, configure OTP, verify email, and so on) at the next sign-in.
  A required action that is disabled in the realm (`listRequiredActions`)
  can block the sign-in.

## 3. Read the recent login errors

Call `getEventSettings` first. When eventsEnabled is false, the realm
records no login events: say so, and continue with the other steps. Do not
turn recording on without the operator.

Otherwise call `listEvents` with the user id (the userId filter takes the
id, not the username), types `LOGIN_ERROR` and `LOGIN`, and a date range
around the failure. Also try the client filter with the clientId of the app.
Read the `error` field of each failure and look it up in
[references/event-errors.md](references/event-errors.md).

A `LOGIN` event after the failures means a later sign-in worked.

## 4. Check lockout

Call `getRealm` and read bruteForce. When it is enabled and the events
show `user_temporarily_disabled`, or `invalid_user_credentials` up to
failureFactor times in a row, the user is likely locked out. With
permanentLockout the user stays disabled until an administrator enables
them; otherwise the lockout ends after the wait time. This server cannot
read or clear a user's lockout; the operator can, on the user's page in
the admin console.

## 5. Check sessions and flows

- `listUserSessions` shows whether the user has a session now, and for
  which clients. A working session for another client points at the client
  or its flow, not the user.
- `listAuthenticationFlows` shows which flow each realm slot uses. Read the
  browser flow (or the direct grant flow, for password grants) with
  `getAuthenticationFlow` when the error points at a step, for example OTP
  required but the user has no OTP set up.
- `getClient` shows whether the app's client is enabled and its redirect
  URIs, for `client_disabled`, `client_not_found` or `invalid_redirect_uri`.
- `getIdentityProvider` shows the provider's settings, when the user signs in
  through one and the error is `identity_provider_error` or
  `identity_provider_login_failure`.

## 6. Report

Give the operator:

- the likely cause, with the evidence (the field or the event and its time),
- the fix, and which tool would make it,
- what you could not check (no events recorded, lockout state not readable).

When the evidence points at more than one cause, list them in order of
likelihood. Make no change until the operator asks for it.
