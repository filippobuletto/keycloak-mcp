# Login event errors

The `error` field of a `LOGIN_ERROR` event, as Keycloak 26 writes it, with
the usual cause and the fix to suggest. Several causes can produce the same
error; check the evidence before naming one.

## The user

| Error | Usual cause | Fix to suggest |
|---|---|---|
| `user_not_found` | Wrong username or email, or the user is in another realm | Check the spelling and the realm with `searchUsers` |
| `invalid_user_credentials` | Wrong password; repeated failures count toward a lockout | The user retries or resets the password; an administrator can set one with `resetPassword` |
| `user_disabled` | An administrator disabled the user, or a permanent lockout did | Enable the user with `updateUser` after checking why it was disabled |
| `user_temporarily_disabled` | Brute-force protection locked the user after failed sign-ins | Wait for the lockout to end, or clear it on the user's page in the admin console |
| `resolve_required_actions` | The user did not complete a required action (update password, configure OTP, verify email) | The user completes it at the next sign-in; check that the action is enabled with `listRequiredActions` |
| `invalid_email` | The user's email address is missing or not valid where one is needed | Check the address with `getUser` and correct it with `updateUser` |
| `credential_not_found` | A flow step needs a credential the user does not have (OTP, passkey) | Add the matching required action, or change the flow with the `flow-change` skill |
| `different_user_authenticated`, `different_user_authenticating` | Another user is already signed in in the same browser | Sign out, or use a private window |

## The session

| Error | Usual cause | Fix to suggest |
|---|---|---|
| `expired_code` | The sign-in page stayed open longer than the login timeout | Start the sign-in again |
| `cookie_not_found` | The browser blocked or lost Keycloak's cookies (third-party cookies, another domain) | Allow the cookies, or use the same domain for app and Keycloak |
| `invalid_authentication_session`, `session_expired` | The sign-in started in another tab or expired | Start the sign-in again |
| `already_logged_in` | The user already has a session for this sign-in | Usually harmless |

## The client

| Error | Usual cause | Fix to suggest |
|---|---|---|
| `client_not_found` | The app uses a clientId the realm does not have | Check the clientId with `listClients` |
| `client_disabled` | The client is disabled | Enable it with `updateClient` |
| `invalid_redirect_uri` | The app's redirect URI is not in the client's list | Add the exact URI with `updateClient`; avoid wildcards |
| `invalid_client_credentials` | The app sends a wrong or old client secret | Update the app's secret, or check it with `getClientSecret` |
| `unauthorized_client`, `not_allowed` | The client may not use this grant (for example direct access grants off) | Check the client's grants with `getClient` |
| `invalid_scope` | The app asks for a scope the client does not have | Check the scopes with `getClientClientScopes` |
| `pkce_verification_failed`, `code_verifier_missing`, `invalid_code_challenge_method` | The app's PKCE settings do not match the client's | Align the app with the client's PKCE method |
| `consent_denied`, `rejected_by_user` | The user declined the consent screen | Nothing to fix unless consent is not wanted |

## The realm and identity providers

| Error | Usual cause | Fix to suggest |
|---|---|---|
| `realm_disabled` | The realm is disabled | An administrator enables the realm in the admin console |
| `ssl_required` | The realm requires HTTPS and the request came over HTTP | Use HTTPS; check `login.sslRequired` with `getRealm` |
| `identity_provider_login_failure`, `identity_provider_error` | The upstream provider refused the sign-in or sent an error | Check the provider with `getIdentityProvider` and the upstream logs |
| `federated_identity_account_exists` | A local user with the same email or username exists | Link the accounts through the first broker login flow |
| `invalid_token`, `invalid_signature`, `invalid_issuer` | A token or signature did not validate (clock skew, rotated keys, wrong issuer URL) | Check the provider's or client's key and issuer settings |
