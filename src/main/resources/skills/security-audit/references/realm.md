# Realm checks

Realm-wide settings, read once with `getRealm` (token and session
lifespans in seconds, refresh token settings, password policy, login
settings, brute-force settings, admin event expiry), `getEventSettings`
and `getServerVersion`. Each check carries the kcwarden auditor name.

## AccessTokenLifespanTooLong

- **Read:** tokens.accessTokenLifespan from `getRealm`.
- **Finding when:** the lifespan is above 600 seconds (10 minutes), or 0 or
  less, which Keycloak reads as no limit.
- **Severity:** High.
- **Why it matters:** an access token cannot be withdrawn once issued, so a
  stolen token works until it expires.
- **Recommended:** 10 minutes or less; apps renew tokens with their refresh
  token.

## AdminEventsStoredWithoutExpiration

From kcwarden main, after v0.19.0.

- **Read:** adminEventsEnabled from `getEventSettings`, and
  adminEventsExpiration (seconds) from `getRealm`.
- **Finding when:** admin events are saved and adminEventsExpiration is
  null or 0 or less.
- **Severity:** Low.
- **Why it matters:** the events then stay in the database forever. They
  hold personal data such as IP addresses and user ids, and the table keeps
  growing.
- **Recommended:** set an expiry; keep long-term audit trails in an external
  log system instead.

## KeycloakVersionShouldBeUpToDate

- **Read:** version from `getServerVersion`. A version containing
  `redhat` is a Red Hat build.
- **Finding when:** the server does not run the latest Keycloak release.
  Compare with the latest release only when you can look it up; otherwise
  report the running version and ask the operator to compare it with the
  releases on keycloak.org. For a Red Hat build the severity drops to Low,
  since fixes may be backported.
- **Severity:** Medium.
- **Why it matters:** only the latest release receives all security fixes.
- **Recommended:** upgrade, and check the running version with a
  vulnerability scanner.

## OfflineSessionMaxLifespanDisabled

- **Read:** tokens.offlineSessionMaxLifespanEnabled from `getRealm`.
- **Finding when:** it is false, so offline sessions have no maximum
  lifespan.
- **Severity:** Info.
- **Why it matters:** an offline token used often enough then never
  expires, which amounts to permanent access.
- **Recommended:** turn on the offline session maximum lifespan.

## PasswordHashingIterationsTooLow

- **Read:** passwordPolicy from `getRealm`. Look for hashAlgorithm(...) and
  hashIterations(...) in the policy string.
- **Finding when:** the policy sets both, the algorithm is a PBKDF2 variant,
  and the iterations are below the minimum: 210,000 for pbkdf2-sha512,
  600,000 for pbkdf2-sha256, 1,300,000 for pbkdf2. No finding for argon2, or
  when the policy sets neither value.
- **Severity:** High.
- **Why it matters:** few iterations make stolen password hashes faster to
  crack.
- **Recommended:** the minimums above (OWASP Password Storage Cheat Sheet),
  or argon2.

## PasswordPolicyMissing

- **Read:** passwordPolicy from `getRealm`.
- **Finding when:** the policy is empty.
- **Severity:** High.
- **Why it matters:** Keycloak then accepts any password, including very
  short or common ones.
- **Recommended:** a policy with at least a minimum length; consider a
  check against known breached passwords.

## RealmBruteForceProtectionDisabled

- **Read:** bruteForce.enabled from `getRealm`.
- **Finding when:** it is false.
- **Severity:** Info.
- **Why it matters:** without it, nothing slows down password guessing.
- **Recommended:** enable brute-force detection.

## RealmEmailVerificationDisabled

- **Read:** login.verifyEmail from `getRealm`.
- **Finding when:** it is false.
- **Severity:** Info.
- **Why it matters:** email addresses are then never confirmed by their
  owner, so apps should not trust them for identity or recovery.
- **Recommended:** require email verification, unless the addresses come
  from a trusted source.

## RealmSelfRegistrationEnabled

- **Read:** login.registrationAllowed from `getRealm`.
- **Finding when:** it is true.
- **Severity:** Info.
- **Why it matters:** anyone can then create an account in the realm.
- **Recommended:** turn it off unless the realm is meant for public sign-up.

## RefreshTokenReuseCountShouldBeZero

- **Read:** tokens.revokeRefreshToken and tokens.refreshTokenMaxReuse from
  `getRealm`.
- **Finding when:** revokeRefreshToken is true and refreshTokenMaxReuse is
  above 0.
- **Severity:** Medium.
- **Why it matters:** each extra reuse widens the window in which a leaked
  refresh token works.
- **Recommended:** a maximum reuse of 0. Revoking refresh tokens can break
  apps that hold several refresh tokens for the same user; test first.

## RefreshTokensShouldBeRevokedAfterUse

- **Read:** tokens.revokeRefreshToken from `getRealm`.
- **Finding when:** it is false.
- **Severity:** Medium.
- **Why it matters:** a leaked refresh token then keeps working for the
  whole session (RFC 9700, section 2.2.2).
- **Recommended:** turn on revocation, so each refresh token works once.
  Test first, as above.

## SsoSessionIdleTimeoutExceedsClientSessionIdleTimeout

- **Read:** tokens.ssoSessionIdleTimeout and
  tokens.clientSessionIdleTimeout from `getRealm`.
- **Finding when:** the SSO session idle timeout is above 3,600 seconds and
  the client session idle timeout is unset (0) or not shorter than it.
- **Severity:** Medium; High from 8 hours; Critical from 24 hours.
- **Why it matters:** clients then inherit the long idle time, and it also
  sets how long refresh tokens stay valid.
- **Recommended:** a client session idle timeout shorter than the SSO
  session idle timeout.

## UserEventsStoredWithoutExpiration

From kcwarden main, after v0.19.0.

- **Read:** eventsEnabled and eventsExpiration from `getEventSettings`.
- **Finding when:** events are saved and the expiry is missing or 0.
- **Severity:** Low.
- **Why it matters:** login events then stay in the database forever. They
  hold personal data such as IP addresses, usernames and email addresses.
- **Recommended:** set an expiry; keep long-term audit trails in an external
  log system instead.
