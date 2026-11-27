# Client checks

List the clients with `listClients`, then read each with `getClient`:
protocol (openid-connect or saml), publicClient, standardFlowEnabled,
directAccessGrantsEnabled, implicitFlowEnabled, serviceAccountsEnabled,
fullScopeAllowed, clientAuthenticatorType, rootUrl, redirectUris,
webOrigins, protocolMappers (the client's own mappers), scopeMappings, and
a set of attributes (PKCE method, lifespan and timeout overrides,
post-logout redirect URIs, refresh token use, device grant, SAML signature
and encryption settings).
Read a client's scopes with `getClientClientScopes` when a check needs them.
Each check carries the kcwarden auditor name.

Which clients a check covers:

- "OIDC" means protocol openid-connect; "SAML" means protocol saml.
- "Confidential" means publicClient false; "public" means publicClient true.
- In the `master` realm, skip the clients named after another realm with
  "-realm" at the end (for example acme-realm); they hold that realm's admin
  roles and have no sign-in of their own.
- "Signs users in" means at least one of standardFlowEnabled,
  directAccessGrantsEnabled, or the attribute
  oauth2.device.authorization.grant.enabled set to true. The implicit flow
  also counts, but this server does not show it; mention that in the report
  when it could change a result.

Redirect URI checks look at each entry of redirectUris. Keycloak treats a
`*` as a wildcard only as the last character. An entry that starts with `/`
is relative to rootUrl.

## ClientAccessTokenLifespanTooLong

- **Read:** attribute access.token.lifespan, for OIDC clients.
- **Finding when:** the attribute is set and above 600 seconds, or 0 or less
  (no limit). No attribute means the realm value applies; the realm check
  covers it.
- **Severity:** High.
- **Why it matters:** a stolen access token works until it expires.
- **Recommended:** remove the override, or set 10 minutes or less.

## ClientAuthenticationViaMTLSOrJWTRecommended

- **Read:** clientAuthenticatorType from `getClient`.
- **Covers:** confidential OIDC clients, except broker and
  realm-management.
- **Finding when:** clientAuthenticatorType is not client-jwt,
  client-secret-jwt, federated-jwt or client-x509; that is, the client
  sends its client secret instead of a signed JWT, a federated JWT, or an
  X.509 certificate (mTLS).
- **Severity:** Info.
- **Why it matters:** a shared secret can leak from configuration or logs and
  is then usable from anywhere; key-based methods never send the secret.
- **Recommended:** a signed JWT or mTLS for clients that can support it.

## ClientHasErroneouslyConfiguredWildcardURI

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry ends in `*` right after the host, for example
  https://app.example.com* (the host part ends in the wildcard), or an entry
  without a scheme and without a slash ends in `*`, for example
  app.example.com*. A lone `*` belongs to
  ClientMustNotUseGlobalWildcardURI instead.
- **Severity:** Critical.
- **Why it matters:** https://app.example.com* also matches
  https://app.example.com.attacker.net, so Keycloak sends authorization codes
  to a domain the attacker owns.
- **Recommended:** end the host with a slash before any wildcard, or better,
  list exact redirect URIs.

## ClientHasNoRedirectUris

- **Read:** redirectUris, for OIDC clients with the standard flow on, except
  Keycloak's own clients (account, account-console, admin-cli, broker,
  realm-management, security-admin-console).
- **Finding when:** the list is empty.
- **Severity:** Medium.
- **Why it matters:** behavior then depends on Keycloak's fallbacks and is
  hard to predict; a sign-in flow needs a known return address.
- **Recommended:** list the app's exact redirect URIs.

## ClientHasUndefinedBaseDomainAndSchema

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry has no scheme (for example /callback or
  app.example.com/cb), so the target cannot be checked here.
- **Severity:** Info.
- **Why it matters:** relative entries depend on rootUrl; the other redirect
  checks cannot judge them.
- **Recommended:** check the entry together with rootUrl, or use absolute
  URIs.

## ClientHasUndefinedBaseDomainAndSchemaInPostLogoutRedirectUri

- **Read:** attribute post.logout.redirect.uris (entries separated by ##),
  for OIDC clients.
- **Finding when:** an entry other than `+` (which means "same as the
  redirect URIs") has no scheme.
- **Severity:** Info.
- **Why it matters:** as above, for the address users return to after
  signing out.
- **Recommended:** absolute URIs, or `+`.

## ClientMustNotUseGlobalWildcardURI

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry is `*`, or its host is `*` and it ends in `*`
  (for example https://*).
- **Severity:** Critical.
- **Why it matters:** Keycloak then sends authorization codes to any
  address, so an attacker can collect them with a crafted link.
- **Recommended:** exact redirect URIs.

## ClientMustNotUseUnencryptedNonlocalRedirectUri

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry uses http:// and its host is not localhost,
  127.0.0.1 or ::1.
- **Severity:** Medium.
- **Why it matters:** codes and tokens then travel unencrypted and can be
  read on the network.
- **Recommended:** https:// for every non-local redirect URI.

## ClientSessionIdleTimeoutNotSetWhileSsoSessionIdleTimeoutTooLong

- **Read:** the client's attribute client.session.idle.timeout from
  `getClient`, and tokens.ssoSessionIdleTimeout and
  tokens.clientSessionIdleTimeout (0 means unset) from `getRealm`.
- **Covers:** OIDC clients.
- **Finding when:** the SSO session idle timeout is above 3,600 seconds and
  either the client sets no override while the realm sets no client session
  idle timeout either, or the client's override is not shorter than the SSO
  session idle timeout.
- **Severity:** Medium.
- **Why it matters:** the client's sessions and refresh tokens then live as
  long as the long SSO idle time.
- **Recommended:** a client session idle timeout shorter than the SSO
  session idle timeout, on the client or for the realm.

## ClientShouldDisableImplicitGrantFlow

- **Read:** implicitFlowEnabled from `getClient`.
- **Covers:** OIDC clients.
- **Finding when:** it is true.
- **Severity:** Medium.
- **Why it matters:** the implicit flow puts tokens in the URL, where they
  leak through history, logs and Referer headers; current OAuth guidance
  drops it.
- **Recommended:** turn it off; use the standard flow with PKCE.

## ClientShouldNotUseWildcardRedirectURI

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry ends in `*` and is not already a finding of
  ClientMustNotUseGlobalWildcardURI.
- **Severity:** Info.
- **Why it matters:** a wildcard path lets codes go to any page under the
  prefix, including pages with open redirects or user content.
- **Recommended:** exact redirect URIs.

## ClientUsesCustomRedirectUriScheme

- **Read:** redirectUris, for OIDC clients with the standard flow on.
- **Finding when:** an entry uses a scheme other than http or https, for
  example myapp://callback.
- **Severity:** Info.
- **Why it matters:** another app on the same device can register the same
  scheme and receive the code; PKCE is then essential.
- **Recommended:** claimed https links where the platform supports them,
  otherwise PKCE with S256.

## ClientWebOriginsMustBeValid

- **Read:** webOrigins, for OIDC clients.
- **Finding when:** an entry other than `+`, `*` or empty is not a bare
  origin: it lacks a scheme or host, or has a path, query or fragment (for
  example https://app.example.com/).
- **Severity:** Info.
- **Why it matters:** browsers compare origins exactly; an invalid entry
  never matches and hides what was intended.
- **Recommended:** scheme, host and port only, or `+` for "same as the
  redirect URIs".

## ClientWebOriginsMustNotUseWildcard

- **Read:** webOrigins, for OIDC clients.
- **Finding when:** the list contains `*`.
- **Severity:** Medium.
- **Why it matters:** any website can then call Keycloak's endpoints for
  this client from a user's browser.
- **Recommended:** list the app's origins, or `+`.

## ClientWithDefaultOfflineAccessScope

- **Read:** default scopes with `getClientClientScopes`, the flags that
  sign users in, and attribute use.refresh.tokens (missing means true).
- **Finding when:** offline_access is a default scope, the client signs
  users in, and it uses refresh tokens.
- **Severity:** Medium.
- **Why it matters:** every sign-in then issues an offline token, which
  outlives the session and logout.
- **Recommended:** remove offline_access from the default scopes; make it
  optional only for clients that need it.

## ClientWithFullScopeAllowed

- **Read:** fullScopeAllowed from `getClient`.
- **Finding when:** it is true.
- **Severity:** Info.
- **Why it matters:** the client's tokens then carry every role the user
  has, not just the roles the client needs.
- **Recommended:** turn it off and map only the roles the client needs.

## ClientWithOptionalOfflineAccessScope

- **Read:** optional scopes with `getClientClientScopes`, the flags that
  sign users in, and attribute use.refresh.tokens.
- **Finding when:** offline_access is an optional scope, the client signs
  users in, and it uses refresh tokens.
- **Severity:** Medium.
- **Why it matters:** the client can ask for offline tokens, which outlive
  the session and logout. Fine when the app needs them; otherwise remove it.
- **Recommended:** keep offline_access only on clients that need offline
  access.

## ClientWithServiceAccountAndOtherFlowEnabled

- **Read:** serviceAccountsEnabled and the flags that sign users in, for
  confidential OIDC clients.
- **Finding when:** the service account is on and the client also signs
  users in.
- **Severity:** Info.
- **Why it matters:** one client then acts both for users and as itself;
  its service account roles and its users' roles mix in one place.
- **Recommended:** separate clients for machine access and user sign-in.

## ConfidentialClientShouldDisableDirectAccessGrants

- **Read:** directAccessGrantsEnabled, for confidential OIDC clients.
- **Finding when:** it is true.
- **Severity:** Medium.
- **Why it matters:** the password grant hands the user's password to the
  app and skips the login page, so OTP, identity providers and brute-force
  checks on the page do not apply.
- **Recommended:** turn it off; use the standard flow.

## ConfidentialClientShouldEnforcePKCE

- **Read:** attribute pkce.code.challenge.method, for confidential OIDC
  clients with the standard flow on, except broker and realm-management.
- **Finding when:** it is not S256.
- **Severity:** Medium.
- **Why it matters:** PKCE stops a stolen authorization code from being
  redeemed, even by someone who knows the client secret.
- **Recommended:** S256.

## PublicClientShouldDisableDirectAccessGrants

- **Read:** directAccessGrantsEnabled, for public OIDC clients.
- **Finding when:** it is true.
- **Severity:** High.
- **Why it matters:** as for confidential clients, and a public client has
  no secret, so anyone can use the password grant through it to guess
  passwords.
- **Recommended:** turn it off.

## PublicClientsMustEnforcePKCE

- **Read:** attribute pkce.code.challenge.method, for public OIDC clients
  with the standard flow on.
- **Finding when:** it is not S256.
- **Severity:** High.
- **Why it matters:** a public client has no secret, so PKCE is the only
  thing that ties the code to the app that asked for it.
- **Recommended:** S256.

## SamlClientHasErroneouslyConfiguredWildcardURI

- **Read:** redirectUris (resolved against rootUrl), for SAML clients.
- **Finding when:** an entry has a wildcard in the host part, as in
  ClientHasErroneouslyConfiguredWildcardURI.
- **Severity:** Critical.
- **Why it matters:** Keycloak then posts SAML assertions to a domain an
  attacker can register.
- **Recommended:** exact assertion consumer URLs.

## SamlClientShouldNotUseWildcardRedirectURI

- **Read:** redirectUris (resolved against rootUrl), for SAML clients.
- **Finding when:** an entry ends in `*`.
- **Severity:** Medium.
- **Why it matters:** assertions can then go to any page under the prefix.
- **Recommended:** exact assertion consumer URLs.

## SamlClientWithAssertionSignatureDisabled

- **Read:** attributes.saml.assertion.signature from `getClient`. In this
  and the other SAML checks, an absent attribute means off (false).
- **Covers:** SAML clients.
- **Finding when:** assertions are not signed (Sign assertions off).
- **Severity:** High.
- **Why it matters:** an unsigned assertion inside a signed response can be
  swapped for a forged one.
- **Recommended:** sign assertions.

## SamlClientWithClientSignatureDisabled

- **Read:** attributes.saml.client.signature from `getClient`.
- **Covers:** SAML clients.
- **Finding when:** Keycloak does not require the client to sign its
  requests (Client signature required off).
- **Severity:** High.
- **Why it matters:** anyone can then send requests that claim to come from
  the client.
- **Recommended:** require client signatures.

## SamlClientWithEncryptionDisabled

- **Read:** attributes.saml.encrypt from `getClient`.
- **Covers:** SAML clients.
- **Finding when:** assertions are not encrypted (Encrypt assertions off).
- **Severity:** Medium.
- **Why it matters:** the assertion passes through the browser readable as
  Base64, exposing the user's attributes.
- **Recommended:** encrypt assertions.

## SamlClientWithWeakSignatureAlgorithm

- **Read:** attributes.saml.signature.algorithm from `getClient`.
- **Covers:** SAML clients.
- **Finding when:** the signature algorithm is RSA_SHA1 or DSA_SHA1.
- **Severity:** Medium.
- **Why it matters:** SHA-1 is open to collision attacks.
- **Recommended:** RSA_SHA256 or stronger.

## SamlClientWithoutOneTimeUseCondition

- **Read:** attributes.saml.onetimeuse.condition from `getClient`.
- **Covers:** SAML clients.
- **Finding when:** the OneTimeUse condition is off.
- **Severity:** Medium.
- **Why it matters:** without it, a captured assertion can be replayed
  within its validity window.
- **Recommended:** turn on the OneTimeUse condition.

## UsingNonDefaultUserAttributesInClientsWithoutUserProfilesFeatureIsDangerous

- **Read:** the client's own mappers of type
  oidc-usermodel-attribute-mapper and their user.attribute config key, from
  protocolMappers in `getClient`, and unmanagedAttributePolicy from
  `getRealm`.
- **Finding when:** a mapper defined on the client itself puts a
  non-standard user attribute into tokens (see the list in
  [client-scopes.md](client-scopes.md)) and users may edit unmanaged
  attributes.
- **Severity:** High.
- **Why it matters:** a user can change the attribute and apps that trust
  the claim act on the changed value.
- **Recommended:** as for the client scope check.
