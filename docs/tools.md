# Tools

† asks you to [confirm](configuration.md#confirmations); unless you accept, the call fails and Keycloak is unchanged.
Write tools refuse the `master` realm, because it holds the Keycloak administrators. List tools return one JSON array;
`searchUsers` and `getGroupMembers` page with `first` and `max` (1 to 500, default 50); `getRealmRole` pages the role's
users and groups the same way.

## Realms

| Tool | Example prompt |
|---|---|
| `listRealms` | "Which realms exist?" |
| `getRealm` | "Show the token lifespans of realm acme." "Does acme lock accounts after failed sign-ins?" "Are refresh tokens revoked after use in acme?" |
| `getServerVersion` | "Which Keycloak version is running?" |
| `createRealm`† | "Create a realm called staging with display name Staging." |
| `deleteRealm`† | "Delete the realm staging." |

`getRealm` and the [realm resource](resources.md) show the password policy as Keycloak's raw string, such as
`length(12) and notUsername`, or `null` when the realm has none. The `bruteForce` block shows whether brute-force
protection is on, its strategy (`MULTIPLE` or `LINEAR`), temporary and permanent lockout, the failures before a
lockout (`failureFactor`), and the wait and reset times in seconds. The `tokens` block also shows refresh token
revocation and reuse, the client session timeouts and the offline session maximum lifespan; `adminEventsExpiration`
is the admin event expiry in seconds (`null` when none is set), and `unmanagedAttributePolicy` is the user profile's
policy for attributes it does not declare (`ENABLED`, `ADMIN_EDIT`, `ADMIN_VIEW`, or `null` when they are off). No tool
changes these settings.

## Users

| Tool | Example prompt |
|---|---|
| `searchUsers` | "List users in realm acme whose email ends with @corp.com." |
| `getUser` | "Show the attributes of user jdoe in realm acme." |
| `getUserGroups` | "Which groups is asmith in, in realm acme?" |
| `getUserRoles` | "Which roles does jdoe have in realm acme?" |
| `createUser` | "Create user cgreen in realm acme with email cgreen@corp.com." |
| `updateUser` | "Disable user bwhite in realm acme." |
| `resetPassword` | "Give jdoe in realm acme a temporary password." |
| `deleteUser`† | "Delete user cgreen from realm acme." |

## Clients

| Tool | Example prompt |
|---|---|
| `listClients` | "List the clients of realm acme." |
| `getClient` | "Show the redirect URIs of client acme-web in realm acme." "Does acme-web allow the implicit flow or full scope?" |
| `getServiceAccount` | "Which roles and groups does the service account of reports-service have in realm acme?" |
| `createClient` | "Create a confidential client billing-api in realm acme with service accounts enabled." |
| `updateClient` | "Add https://app.acme.test/* to the redirect URIs of acme-web in realm acme." |
| `deleteClient`† | "Delete the client billing-api from realm acme." |

## Client secrets

These tools exist only when the server runs with `MCP_CLIENT_SECRETS_ENABLED=true` (see
[Client secrets](configuration.md#client-secrets) under Configuration). Both refuse `master` and public or bearer-only
clients.

| Tool | Example prompt |
|---|---|
| `getClientSecret`† | "What is the secret of reports-service in realm acme?" |
| `regenerateClientSecret`† | "The billing-api secret leaked in realm acme; rotate it." |

## Client scopes

Scopes are addressed by name. The list and get tools show SAML scopes too; the write tools change OpenID Connect
scopes only and refuse a SAML scope. `deleteClientScope` refuses the scopes Keycloak creates in every realm, such as
`roles` and `profile`, because deleting one changes every token in the realm. Those scopes can still be updated and
assigned.

| Tool | Example prompt |
|---|---|
| `listClientScopes` | "Which client scopes does realm acme have, and which ones does every new client get?" |
| `getClientScope` | "Why is email_verified missing from the ID token in realm acme?" (shows each mapper's config) |
| `getClientClientScopes` | "Why doesn't reports-service get the roles claim in realm acme?" |
| `createClientScope` | "Create a client scope billing:read in acme, shown on the consent screen as 'Read your invoices'." |
| `updateClientScope` | "Stop listing billing:read in the token's scope claim in realm acme." |
| `deleteClientScope`† | "Delete the client scope billing:read from realm acme." |
| `addClientScopeToClient` | "Add billing:read to client acme-web as an optional scope in realm acme." |
| `removeClientScopeFromClient` | "Remove billing:read from client acme-web in realm acme." |
| `setRealmDefaultClientScope`† | "Make billing:read a default scope for every new client in acme." |
| `addClientScopeRole` | "Limit the billing:read scope to the realm role viewer in acme." |
| `removeClientScopeRole` | "Remove the viewer role from the billing:read scope in acme." |

## Client scope mappers

Protocol mappers decide which claims a scope puts into tokens. They are addressed by name within their scope, on
OpenID Connect scopes only, built-in scopes included. Before a write, the mapper type id and every supplied config key
are checked against the types the server reports; an unknown one is refused with the valid keys, and a new mapper
must have every key its type marks required. Updates change only the supplied keys.

| Tool | Example prompt |
|---|---|
| `listProtocolMapperTypes` | "Which mapper types can I use, and what config does a hardcoded claim need?" |
| `addClientScopeMapper` | "Add a claim tenant with value acme to the access token of clients that use billing:read in acme." |
| `updateClientScopeMapper` | "Stop putting the client roles of the roles scope into the userinfo response in realm acme." |
| `deleteClientScopeMapper`† | "Remove the tenant claim from billing:read in realm acme." |

## Identity providers

Identity providers are the external sign-ins of a realm (OpenID Connect, SAML, social). They are addressed by alias.
`createIdentityProvider` takes a type id that `listIdentityProviderTypes` shows and a config map, which Keycloak checks.
Updates change only the supplied fields and config keys. Disabling an enabled provider stops every sign-in through it,
so `updateIdentityProvider` asks you to [confirm](configuration.md#confirmations) that change only.

A `clientSecret` in the config is write-only. The create and update tools accept it without
`MCP_CLIENT_SECRETS_ENABLED`, and no tool returns it: results show `**********`. An update that leaves the secret out
keeps the stored one, unless it changes where the secret is sent (`tokenUrl`, `tokenIntrospectionUrl`, `clientId`,
`baseUrl` or `clientAuthMethod`). Keycloak then refuses with "Client secret must be re-entered ...", and the update must
supply `clientSecret` again.

| Tool | Example prompt |
|---|---|
| `listIdentityProviderTypes` | "Which identity provider types does this Keycloak support?" |
| `listIdentityProviders` | "Which identity providers does acme have, and which are disabled?" |
| `getIdentityProvider` | "Why do users who sign in through corporate-oidc skip the review profile page in acme?" |
| `createIdentityProvider` | "Add a Google sign-in to acme with client id X and secret Y." |
| `updateIdentityProvider` | "Point corporate-oidc in acme at the authorization URL https://idp.corp/v2/auth." |
| `updateIdentityProvider`† (disable) | "Turn off the github provider in realm acme." |
| `deleteIdentityProvider`† | "Remove the old-saml provider from realm acme." |

`getIdentityProvider` also lists the provider's mappers, described next.

## Identity provider mappers

Identity provider mappers decide what Keycloak copies from an external sign-in into the local user: attributes, roles,
groups, the username, or session notes. They are addressed by name within their provider. Which mapper types a
provider can use depends on its type, so `listIdentityProviderMapperTypes` takes the provider's alias. Before a write,
the mapper type id is checked against those types and every supplied config key against the type; an unknown one is
refused with the valid ones. Keycloak itself stores a mapper of any type. Updates change only the supplied keys.

The sync mode decides on which sign-ins a mapper applies. It is the `syncMode` parameter, not a config key:

| Sync mode | Effect |
|---|---|
| `INHERIT` | Uses the provider's sync mode (its `syncMode` config key). The default for new mappers |
| `IMPORT` | Applies on the user's first sign-in through the provider only |
| `FORCE` | Applies on every sign-in, and overwrites what the user or an admin changed in between |
| `LEGACY` | Keycloak's earlier behavior; a mapper without a stored sync mode runs as `LEGACY` |

| Tool | Example prompt |
|---|---|
| `listIdentityProviderMapperTypes` | "Which mapper types can corporate-oidc use, and what config does a claim to attribute mapper need?" |
| `addIdentityProviderMapper` | "Copy the department claim from corporate-oidc into the user attribute department in acme." |
| `updateIdentityProviderMapper` | "Make the email mapper of corporate-oidc in acme apply on every sign-in." |
| `deleteIdentityProviderMapper`† | "Remove the old group mapper from corporate-oidc in realm acme." |

## Authentication flows

Authentication flows decide how a realm signs users in: which steps run (password, OTP, an identity provider
redirect, ...), in which order, and which of them are required. Flows are addressed by alias. Steps and sub-flows of a
flow (executions) are addressed by the id that `getAuthenticationFlow` shows. `listAuthenticationFlows` shows what
uses each flow: realm slots such as `realm: BROWSER`, client overrides such as `client kiosk: BROWSER`, and identity
providers such as `identity provider corp: FIRST_BROKER_LOGIN`.

Built-in flows stay as Keycloak ships them, so a working sign-in is always there to bind back. The step and config
tools refuse a built-in flow. To change one, copy it, change the copy, then bind the copy:

1. `copyAuthenticationFlow` from `browser` to, say, `browser-otp`. A copy affects no sign-in yet.
2. Change the copy: `addFlowExecution`, `addFlowSubFlow`, `updateFlowExecution`, `moveFlowExecution` and
   `setExecutionConfig` need no confirmation.
3. `bindRealmFlow`† makes every sign-in of that kind in the realm run the copy at once; `setClientFlowOverride`† does
   that for one client. `bindRealmFlow` returns the flow it replaced, so you can bind it back.

Before each write the tools check what Keycloak does not: a requirement the step does not allow, a config key its
type does not declare, a config on a step that takes none, an execution id from another flow, and a move past the
first or last step. A flow in use cannot be deleted; the refusal names what uses it. A bind needs a top-level flow of
the right type: `CLIENT_AUTHENTICATION` takes a `client-flow`, every other slot and client override a `basic-flow`.

| Tool | Example prompt |
|---|---|
| `listAuthenticationFlows` | "Which flows does acme have, and which one runs for browser sign-in?" |
| `getAuthenticationFlow` | "Does acme's browser sign-in ask for OTP?" |
| `listAuthenticatorTypes` | "Which config does the Identity Provider Redirector step take?" |
| `createAuthenticationFlow` | "Create an empty flow kiosk-login in acme." |
| `copyAuthenticationFlow` | "Copy acme's browser flow to browser-otp." |
| `addFlowExecution` | "Add an OTP step at the end of browser-otp's forms sub-flow." |
| `addFlowSubFlow` | "Add a sub-flow step-up to kiosk-login." |
| `updateFlowExecution` | "Make OTP required in browser-otp." |
| `moveFlowExecution` | "Run Kerberos before Cookie in browser-otp." |
| `deleteFlowExecution`† | "Remove the WebAuthn step from browser-otp." |
| `setExecutionConfig` | "Send browser-otp's users straight to corporate-oidc." |
| `deleteExecutionConfig`† | "Remove the redirector config from browser-otp." |
| `bindRealmFlow`† | "Use browser-otp for browser sign-in in acme." |
| `setClientFlowOverride`† | "Let the kiosk client sign in with kiosk-login." |
| `deleteAuthenticationFlow`† | "Delete the old browser-2fa-test flow from acme." |

## Required actions

Required actions are steps Keycloak asks a user to finish at sign-in, such as `CONFIGURE_TOTP` (set up OTP),
`VERIFY_EMAIL` or `UPDATE_PASSWORD`. They are addressed by alias. A disabled action is never asked for, even when a
user has it pending; a default action is given to every new user. Keycloak asks for them in the order
`listRequiredActions` shows.

`updateRequiredAction` asks you to [confirm](configuration.md#confirmations) only two changes: disabling an enabled
action, and turning on a default action. When the action sets up a credential a flow can require (`CONFIGURE_TOTP` for
OTP, `webauthn-register` and `webauthn-register-passwordless` for WebAuthn), the dialog names every flow in use that
requires that step: with the setup action off, users without the credential cannot sign in through it. A step inside a
conditional sub-flow, as in the built-in `browser` flow, runs only for users who already have the credential and is not
named.

Keycloak replaces a whole required action, or a whole config, on every update, and drops config keys it does not
know without an error. The tools send the stored values back with your change, refuse undeclared config keys, and
refuse a move past the first or last action.

| Tool | Example prompt |
|---|---|
| `listRequiredActions` | "Is OTP setup enabled in acme, and do new users have to do it?" |
| `getRequiredAction` | "Which config does UPDATE_PASSWORD take in acme?" |
| `updateRequiredAction` | "Turn on Update Email in acme." |
| `updateRequiredAction`† (disable or default) | "Make every new acme user set up OTP." |
| `moveRequiredAction` | "Ask for email verification before profile update in acme." |
| `setRequiredActionConfig` | "Require re-authentication after 5 minutes before a password change in acme." |
| `deleteRequiredActionConfig` | "Reset the UPDATE_PASSWORD config in acme to its defaults." |

## Sessions

A session is a user's sign-in. A `REGULAR` session lasts while the user or app keeps using it; an `OFFLINE` session
stands behind an offline token, survives the regular session and has its own id. Sessions are addressed by the id the
list tools show, users by id, clients by `clientId`.

`deleteSession` ends one session without asking; the user can sign in again. Set `offline=true` for an offline session:
with the wrong value nothing ends, and the error says which value to use. `logoutUser` and `logoutRealm` ask you to
[confirm](configuration.md#confirmations), and the dialog says how many sessions will end. Both also make Keycloak
reject the tokens issued before the logout. Keycloak's own user logout leaves offline sessions listed with tokens that
no longer work, so `logoutUser` deletes them too.

| Tool | Example prompt |
|---|---|
| `listUserSessions` | "Where is jdoe signed in, in realm acme?" |
| `listClientSessions` | "Who is using the portal client in acme right now?" |
| `getSessionStats` | "How many sessions does each acme client have?" |
| `deleteSession` | "End jdoe's session from 10.0.0.7 in acme." |
| `logoutUser`† | "Sign jdoe out everywhere in acme." |
| `logoutRealm`† | "Sign everyone out of realm staging." |

## Events

Keycloak records two kinds of events per realm, as the realm's event settings say: login events (sign-ins, sign-in
errors, logouts, token and account actions) and admin events (changes made through the admin API or console). New
realms record neither. Both lists come newest first, 50 at a time by default (up to 500), with filters that must all
match. Login events filter by type, `clientId`, user id, IP address and dates; admin events by operation and resource
type, resource path (`*` matches any part, as in `groups/*`), who acted (realm name, `clientId`, user id, IP address)
and dates. Unknown type names fail with a message naming them. Admin events show the JSON sent with each change only
when you ask for it, and only while the realm records it.

`updateEventSettings` changes only the settings you give. It asks you to [confirm](configuration.md#confirmations) when
the change turns login or admin events off, or removes a listener. `clearEvents` deletes every stored event of one kind
and always asks.

| Tool | Example prompt |
|---|---|
| `getEventSettings` | "Which events does acme record?" |
| `updateEventSettings` | "Record login and admin events in acme and keep login events for 7 days." |
| `updateEventSettings`† (stop) | "Stop recording admin events in acme." |
| `listEvents` | "Why can't jdoe sign in to acme? Show the recent sign-in errors." |
| `listAdminEvents` | "Who changed the groups in acme this week, and what did they send?" |
| `clearEvents`† | "Delete the login history of realm staging." |

## Roles

| Tool | Example prompt |
|---|---|
| `listRealmRoles` | "List the realm roles of acme." |
| `listClientRoles` | "Which roles does client reports-service define in realm acme?" |
| `getRealmRole` | "Who holds the viewer role in acme, and which roles does default-roles-acme contain?" |
| `createRealmRole` | "Create a realm role auditor in acme." |
| `assignRealmRole` | "Give jdoe the editor role in realm acme." |
| `unassignRealmRole` | "Remove the editor role from jdoe in realm acme." |
| `deleteRealmRole`† | "Delete the realm role auditor from acme." |

## Groups

| Tool | Example prompt |
|---|---|
| `listGroups` | "Show the group tree of realm acme." |
| `getGroup` | "Which roles does group /finance grant in realm acme?" |
| `getGroupMembers` | "Who is in /finance/payables in realm acme?" |
| `createGroup` | "Create a group audit under /finance in realm acme." |
| `addUserToGroup` | "Add user jdoe to group finance in realm acme." |
| `removeUserFromGroup` | "Remove jdoe from /finance in realm acme." |
| `deleteGroup`† | "Delete the group /finance/audit in realm acme." |
