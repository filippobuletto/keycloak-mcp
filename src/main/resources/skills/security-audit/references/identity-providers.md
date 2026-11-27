# Identity provider checks

List the providers with `listIdentityProviders`, then read each with
`getIdentityProvider`: its providerId (oidc, keycloak-oidc, saml, or a
social provider such as github), its config keys, and its mappers. A config
key that is missing counts as false. Each check carries the kcwarden
auditor name.

## IdentityProviderWithMappersWithoutForceSyncMode

- **Read:** config syncMode and the mappers, from `getIdentityProvider`.
- **Finding when:** the provider has at least one mapper and its syncMode is
  not FORCE (a missing syncMode means LEGACY).
- **Severity:** Medium.
- **Why it matters:** mappers then copy roles, groups or attributes from the
  upstream provider only at the first sign-in. When the upstream provider
  later removes a role, the user keeps it in Keycloak.
- **Recommended:** syncMode FORCE on the provider, or on the mappers whose
  data must follow upstream. Ignore the finding when the one-time copy is
  intended.

## IdentityProviderWithOneTimeSync

- **Read:** config syncMode from `getIdentityProvider`.
- **Finding when:** syncMode is not FORCE.
- **Severity:** Info.
- **Why it matters:** changes to name and email in the upstream provider do not
  reach Keycloak after the first sign-in.
- **Recommended:** FORCE when user data must follow upstream; otherwise
  accept the finding.

## IdentityProviderWithSignatureVerificationDisabled

- **Read:** providerId and config validateSignature, from
  `getIdentityProvider`. Only for providerId oidc and keycloak-oidc; social
  providers always verify.
- **Finding when:** validateSignature is not true.
- **Severity:** Critical.
- **Why it matters:** Keycloak then accepts tokens from the provider without
  checking that the provider signed them, so a forged token can sign in as
  any user.
- **Recommended:** validateSignature true, with the provider's JWKS URL or
  public key.

## OIDCIdentityProviderWithoutPKCE

- **Read:** providerId and config pkceEnabled and pkceMethod, from
  `getIdentityProvider`. Only for providerId oidc and keycloak-oidc.
- **Finding when:** pkceEnabled is not true, or pkceMethod is not S256.
- **Severity:** Medium.
- **Why it matters:** without PKCE, an intercepted authorization code can be
  exchanged by someone else.
- **Recommended:** pkceEnabled true with pkceMethod S256.

## SamlIdentityProviderWithSignatureVerificationDisabled

- **Read:** config validateSignature, for providerId saml.
- **Finding when:** validateSignature is not true.
- **Severity:** Critical.
- **Why it matters:** Keycloak then accepts SAML responses nobody signed, so
  an attacker can forge one and sign in as any user, administrators
  included.
- **Recommended:** validateSignature true, with the provider's signing
  certificate.

## SamlIdentityProviderWithoutEncryptedAssertions

- **Read:** config wantAssertionsEncrypted, for providerId saml.
- **Finding when:** it is not true.
- **Severity:** Medium.
- **Why it matters:** the assertion passes through the browser readable as
  Base64, exposing personal data such as email and group memberships to
  browser extensions and proxy logs.
- **Recommended:** wantAssertionsEncrypted true.

## SamlIdentityProviderWithoutPostBindingResponse

- **Read:** config postBindingResponse, for providerId saml.
- **Finding when:** it is not true, so responses travel in the URL
  (HTTP-Redirect).
- **Severity:** Medium.
- **Why it matters:** URLs end up in browser history, server and proxy logs,
  and Referer headers. Large responses can also exceed URL length limits
  and break sign-in for users with many roles.
- **Recommended:** postBindingResponse true (HTTP-POST).

## SamlIdentityProviderWithoutSignedAssertions

- **Read:** config wantAssertionsSigned, for providerId saml.
- **Finding when:** it is not true.
- **Severity:** High.
- **Why it matters:** with only the outer response signed, an attacker can
  swap in a forged assertion inside a valid response.
- **Recommended:** wantAssertionsSigned true, together with
  validateSignature.

## SamlIdentityProviderWithoutSignedAuthnRequests

- **Read:** config wantAuthnRequestsSigned, for providerId saml.
- **Finding when:** it is not true.
- **Severity:** Medium.
- **Why it matters:** the provider cannot tell that a sign-in request came
  from Keycloak, which helps login CSRF and similar attacks.
- **Recommended:** wantAuthnRequestsSigned true.
