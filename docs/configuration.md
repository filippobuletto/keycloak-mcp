# Configuration

Set these as environment variables. They apply to the `stdio` and `http` profiles and to the default production
profile.

## Target Keycloak

| Variable | Default | Meaning |
|---|---|---|
| `KEYCLOAK_URL` | none, required | Base URL of the Keycloak the tools administer |
| `KEYCLOAK_AUTH_REALM` | `master` | Realm the server authenticates against |
| `KEYCLOAK_CLIENT_ID` | `admin-cli` | Client used to get the admin token |
| `KEYCLOAK_GRANT_TYPE` | `PASSWORD` | `PASSWORD` or `CLIENT_CREDENTIALS` |
| `KEYCLOAK_USERNAME` | empty | Admin user, for `PASSWORD` |
| `KEYCLOAK_PASSWORD` | empty | Admin password, for `PASSWORD` |
| `KEYCLOAK_CLIENT_SECRET` | empty | Client secret, for `CLIENT_CREDENTIALS` |

Every variable but `KEYCLOAK_URL` sets the server's admin identity, which only the `stdio` profile has. Over stdio the
tools act with the permissions of this identity, so give it only the roles the tools you use need. The HTTP server
(the `http` and default production profiles) ignores these variables: it acts as the signed-in user, see
[HTTP authentication](#http-authentication).

## Grant types

These apply to the `stdio` profile. `PASSWORD` is the default and logs in as an admin user through `admin-cli`.

`CLIENT_CREDENTIALS` logs in as a confidential client's service account, so no user password is needed. Since
Keycloak 26.8, that client needs the `kc-admin-api` client scope, or every tool call fails with
`Keycloak returned 401 Unauthorized`. [Admin identity](connect.md#admin-identity) shows how to set up such a client on
your Keycloak, and [Service account](development.md#service-account) does the same against the compose Keycloak.

## HTTP authentication

| Variable | Default | Meaning |
|---|---|---|
| `MCP_OIDC_URL` | `${KEYCLOAK_URL}/realms/master` | Issuer of the bearer tokens that clients send to `/mcp`; a realm of the Keycloak at `KEYCLOAK_URL` |
| `MCP_OIDC_AUDIENCE` | `keycloak-mcp-server` | Audience every token must carry |
| `MCP_OIDC_CLIENT_ID` | `keycloak-mcp-server` | Confidential client of the issuer realm that exchanges the users' tokens |
| `MCP_OIDC_CLIENT_SECRET` | none, required | That client's secret |
| `MCP_OIDC_ROLE` | `mcp-admin` | Realm role every token must carry |
| `MCP_OIDC_SCOPE` | `mcp:tools` | Client scope that clients request at sign-in; it must exist in the issuer realm |
| `MCP_PUBLIC_URL` | unset | The `/mcp` URL as clients reach it, reported in the OAuth metadata |

Requests to `/mcp` get these answers:

| Request | Answer |
|---|---|
| No token or an invalid token | `401 Unauthorized` |
| Valid token without the role | `403 Forbidden` with `WWW-Authenticate: Bearer error="insufficient_scope", scope="mcp:tools", ...` |
| Valid token with the role | The MCP response |

For each request that calls Keycloak, the server exchanges the user's token at the issuer realm (standard token
exchange, with `scope=kc-admin-api`) and calls the Admin REST API with the result, so Keycloak checks the user's own
admin roles. A failed exchange, for example after the user's session ended, fails the tool call with
`Token exchange at the issuer failed:` and Keycloak's error; the server never falls back to another identity. The
exchange works only inside one Keycloak, so the issuer must be a realm of the Keycloak at `KEYCLOAK_URL`:

| Setup | Configuration |
|---|---|
| Users of `master` manage the realms their admin roles allow (default) | Set only `KEYCLOAK_URL` |
| Users of one realm manage only that realm, with roles of its `realm-management` client | `MCP_OIDC_URL=${KEYCLOAK_URL}/realms/<realm>` |

[Issuer realm](connect.md#issuer-realm) lists the clients, scope and role the issuer realm needs.

## Browser clients (CORS)

Clients that run outside a browser, such as Claude Code, send no `Origin` header and need no CORS setup. A web page
can call `/mcp` only from an origin listed in `MCP_CORS_ORIGINS`:

| Variable | Default | Meaning |
|---|---|---|
| `MCP_CORS_ORIGINS` | empty: same-origin requests only | Comma-separated browser origins trusted to call `/mcp` |

```shell
MCP_CORS_ORIGINS=https://console.example.com,https://admin.example.com
```

- The server refuses to start when the list contains a bare `*`, because that would let any website call `/mcp`.
- A value enclosed in `/` is a regular expression, and the server logs a warning for each one at startup. The list
  treats a backslash as an escape character, so write `\\.` for a literal dot. A single `\.` becomes `.`, which
  matches any character and trusts more origins than intended. In a properties file, write four backslashes.

```shell
MCP_CORS_ORIGINS='/https://[a-z0-9-]+\\.corp\\.example\\.com/'
```

- Browsers never send cookies: the server allows no credentials, and clients authenticate with the bearer token.
- A request from an origin that isn't listed gets `403`. The token check still applies to listed origins.

## OAuth sign-in

The `401` answer points MCP clients at the server's OAuth protected resource metadata (RFC 9728), which names the
issuer realm and the scope to request:

| `MCP_PUBLIC_URL` | Metadata URL | `resource` in it |
|---|---|---|
| `https://mcp.example.com/mcp` | `https://mcp.example.com/.well-known/oauth-protected-resource/mcp` | `https://mcp.example.com/mcp` |
| unset | `https://<request host>/.well-known/oauth-protected-resource` | `https://<request host>` |

Set `MCP_PUBLIC_URL` whenever clients sign in with OAuth. Claude Code checks that the advertised resource is the URL
it connected to. Behind a proxy, use the public URL. Run the HTTP transport behind TLS whenever it is reachable from
other machines.

The scope only makes the sign-in request valid; access still needs the `MCP_OIDC_ROLE` role. Create the
`MCP_OIDC_SCOPE` client scope in the issuer realm, and a public client for Claude Code with redirect URI
`http://localhost:33418/callback` and an audience mapper for `MCP_OIDC_AUDIENCE`.

## Confirmations

The tools marked † below ask you to confirm before they change anything, and so do `updateIdentityProvider` when it
disables an enabled provider, `updateRequiredAction` when it disables an enabled action or turns on a default
action, and `updateEventSettings` when it stops recording events:

| Variable | Default | Meaning |
|---|---|---|
| `MCP_CONFIRM_MODE` | `elicit` | `elicit`: the server sends a confirmation dialog through the MCP client. `flag`: the call needs `confirm=true`, which the client sets |
| `QUARKUS_MCP_SERVER_ELICITATION_DEFAULT_TIMEOUT` | `60s` | How long the server waits for an answer to the dialog |

- In `elicit` mode the dialog names the action, the target, the realm and the consequence, and has one checkbox. The
  tool runs only if you accept with the box ticked. Declining, cancelling, leaving the box unticked, or not answering
  in time fails the call, and Keycloak is unchanged. The `confirm` argument is ignored.
- `elicit` mode needs a client that supports MCP elicitation in form mode, such as Claude Code. A client without it
  gets an error that names `MCP_CONFIRM_MODE=flag`, and nothing runs.
- On connections with MCP protocol revision 2026-07-28, the server returns the dialog as an `input_required` result
  and the client asks you before it retries the call. The rules are the same.
- `flag` mode is for clients without elicitation. There the client decides on its own whether to send
  `confirm=true`; see [Before you use it](risks.md).

## Client secrets

`getClientSecret` and `regenerateClientSecret` return a confidential client's live secret. They are off by default:

| Variable | Default | Meaning |
|---|---|---|
| `MCP_CLIENT_SECRETS_ENABLED` | `false` | `true` lists both tools and lets them run, over stdio and HTTP |

- With the variable unset, the tools are missing from the tool list and a call to either fails as an unknown tool.
- Every call needs a [confirmation](#confirmations), the read included.
- `regenerateClientSecret` invalidates the old secret at once, so every app still using it fails to authenticate
  until it gets the new one.
- The server never logs a secret, and it refuses to start with the variable on when
  `quarkus.mcp.server.traffic-logging.enabled=true` or `quarkus.rest-client.logging.scope` is set, because either
  would write the secrets to the log.
- The secret does reach the MCP client and ends up in its conversation history. Turn the variable on only where that
  history may hold credentials, and leave it off on shared HTTP deployments unless every `mcp-admin` user may see
  every client secret of the target Keycloak.
