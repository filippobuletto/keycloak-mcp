# Connect Claude Code

This page connects Claude Code to a Keycloak you run, here `https://keycloak.example.com`. To try the server against
the local compose Keycloak instead, see [Development](development.md#local-keycloak).

Pick one of two setups:

| Setup | Where the server runs | Who signs in to Keycloak |
|---|---|---|
| [stdio](#stdio) | On your machine; Claude Code starts it for each session | Only the server, with its [admin identity](#admin-identity) |
| [Shared HTTP server](#shared-http-server) | Once, for example at `https://mcp.example.com/mcp` | Each user through the browser; the server calls Keycloak as that user |

With stdio the tools act with the permissions of the admin identity, whoever asks. With the shared HTTP server each
user acts with their own Keycloak admin roles. Read [Before you use it](risks.md) first.

## Get the server

Each release on the [releases page](https://github.com/filippobuletto/keycloak-mcp/releases) publishes two container
images and, for running without Docker, the JVM application and native binaries.

### Container images

Both images are built for `linux/amd64` and `linux/arm64`; Docker pulls the one for your CPU. `<version>` is a
release, for example `0.1.0`:

| Image | Contents |
|---|---|
| `ghcr.io/filippobuletto/keycloak-mcp:<version>` | The server on a Java runtime reduced to the modules it uses |
| `ghcr.io/filippobuletto/keycloak-mcp:<version>-native` | The native build: same server and settings, starts in a few hundredths of a second |
| `ghcr.io/filippobuletto/keycloak-mcp:latest` | The native image of the latest release, not of pre-releases |

Both images:

- contain no shell and no package manager, so `docker exec` into them has nothing to run;
- run as the non-root user 65532;
- write nothing to their filesystem, so they run with `docker run --read-only`.

Both run the HTTP transport on port 8080 by default. To switch to stdio, pass `-e QUARKUS_PROFILE=stdio` to
`docker run` for the Java image, or add `-Dquarkus.profile=stdio` after the image name for the native one: arguments
after the image name go to the native server, while the Java image passes them to the application, not to Java.

### Without Docker

The release assets are:

| Asset | Runs on |
|---|---|
| `keycloak-mcp-<version>.zip` | Any OS with Java 25 |
| `keycloak-mcp-<version>-linux-amd64` | Linux on x86-64 |
| `keycloak-mcp-<version>-linux-arm64` | Linux on ARM64 |
| `SHA256SUMS` | The SHA-256 checksums of the three files above |

Check the files you downloaded against `SHA256SUMS`, in the directory that holds them:

```bash
sha256sum -c --ignore-missing SHA256SUMS
```

The zip holds the directory `quarkus-app`; run the server with `java -jar quarkus-app/quarkus-run.jar`. The binary
needs the execute bit (`chmod +x keycloak-mcp-<version>-linux-amd64`). Both take the same variables as the images and
run HTTP by default; for stdio, put `-Dquarkus.profile=stdio` before `-jar` for the zip, or after the binary.

To build the server from source instead, see [Development](development.md).

## Admin identity

Over stdio the server calls the Keycloak Admin REST API with its own identity. The shared HTTP server has none: it
acts as the signed-in user (see [Issuer realm](#issuer-realm)). A service account fits best: it has no password
and no sign-in of its own, and you can rotate its secret. Create it in the `master` realm of
`https://keycloak.example.com/admin`:

1. Under Clients, create an OpenID Connect client, for example `keycloak-mcp-admin`. Turn on Client authentication
   and Service account roles, and turn off Standard flow and Direct access grants.
2. On its Client scopes tab, add the client scope `kc-admin-api` as a Default scope. Since Keycloak 26.8 the Admin
   REST API accepts only tokens with that scope; Keycloak gives it to `admin-cli` but not to new clients, so without
   it every tool call fails with `Keycloak returned 401 Unauthorized`.
3. On its Service account roles tab, assign the roles the tools you use need. The realm role `admin` lets every tool
   work, `createRealm` and `deleteRealm` included. To limit the server to some realms, assign roles of the
   `<realm>-realm` clients of `master` instead, such as `view-users` or `manage-clients` of `acme-realm`. A tool that
   needs a role the account lacks fails with Keycloak's error and changes nothing.
4. Copy the secret from its Credentials tab.

The server then needs these variables; [Configuration](configuration.md#target-keycloak) lists them all:

```bash
KEYCLOAK_URL=https://keycloak.example.com
KEYCLOAK_GRANT_TYPE=CLIENT_CREDENTIALS
KEYCLOAK_CLIENT_ID=keycloak-mcp-admin
KEYCLOAK_CLIENT_SECRET=<secret>
```

The default `PASSWORD` grant signs in as an admin user through `admin-cli` instead; see
[Grant types](configuration.md#grant-types).

## stdio

Claude Code starts the image for each session and talks to it over stdin and stdout. Nothing listens on a port, and
no issuer realm is needed:

```bash
claude mcp add keycloak \
  -e KEYCLOAK_URL=https://keycloak.example.com \
  -e KEYCLOAK_GRANT_TYPE=CLIENT_CREDENTIALS \
  -e KEYCLOAK_CLIENT_ID=keycloak-mcp-admin \
  -e KEYCLOAK_CLIENT_SECRET=<secret> \
  -- docker run -i --rm --read-only \
  -e KEYCLOAK_URL -e KEYCLOAK_GRANT_TYPE -e KEYCLOAK_CLIENT_ID -e KEYCLOAK_CLIENT_SECRET \
  ghcr.io/filippobuletto/keycloak-mcp:<version>-native -Dquarkus.profile=stdio
```

`docker run -e NAME` without a value passes the variable from the environment Claude Code gives the command, so the
secret is not part of the `docker` arguments. Claude Code stores the `-e` values in its configuration in plain text;
add `--scope user` to make the server available in every project.

## Shared HTTP server

One server serves many users. Each user signs in through the browser, and the server accepts only tokens that carry
a role you choose. For every request it exchanges the user's token at the issuer realm for one the Admin REST API
accepts (standard token exchange), so Keycloak sees the user and checks the user's own admin roles.

### Issuer realm

Users sign in to an issuer realm, by default `master` of the same Keycloak. The exchange works only inside one
Keycloak, so the issuer must be a realm of the Keycloak at `KEYCLOAK_URL`. Add to it:

- the client scope `mcp:tools`;
- the realm role `mcp-admin`, granted to the users who may use the server;
- a confidential client `keycloak-mcp-server` for the exchange: Client authentication and Standard Token Exchange on;
  Standard flow, Direct access grants and Service account roles off; on its Client scopes tab, `kc-admin-api` as an
  Optional scope. Copy the secret from its Credentials tab;
- a public client `keycloak-mcp` with Standard flow on, the redirect URI `http://localhost:33418/callback`, `mcp:tools`
  as an optional client scope, and an audience mapper that adds `keycloak-mcp-server` to the access token.

What each user can do depends on their admin roles in `master`, as for a service account in
[Admin identity](#admin-identity): the realm role `admin` lets every tool work, `createRealm` included, and roles of
the `<realm>-realm` clients, such as `view-users` of `acme-realm`, limit the user to those realms. A tool call outside
the user's roles fails with `Keycloak returned 403 Forbidden` and changes nothing.

To serve one realm only, make it the issuer with `MCP_OIDC_URL=https://keycloak.example.com/realms/<realm>` and add
the objects above to it. Its users then need roles of its `realm-management` client, and the server can manage only
that realm.

Keycloak's admin events record each change made through the server with the user's id (`authDetails.userId`), the
internal id of `keycloak-mcp-server` (`authDetails.clientId`) and the IP address of the server, not the user's.

The names are defaults. [HTTP authentication](configuration.md#http-authentication) shows how to change them and how
the server answers a token without the role.

### Run the server

Run the image behind a reverse proxy that terminates TLS for `https://mcp.example.com` and forwards to port 8080:

```bash
export MCP_OIDC_CLIENT_SECRET=<secret of keycloak-mcp-server>
docker run -d --name keycloak-mcp --read-only -p 127.0.0.1:8080:8080 \
  -e KEYCLOAK_URL=https://keycloak.example.com \
  -e MCP_OIDC_CLIENT_SECRET \
  -e MCP_PUBLIC_URL=https://mcp.example.com/mcp \
  ghcr.io/filippobuletto/keycloak-mcp:<version>-native
```

The HTTP server needs no admin identity, so the `KEYCLOAK_GRANT_TYPE`, `KEYCLOAK_USERNAME`, `KEYCLOAK_PASSWORD` and
`KEYCLOAK_CLIENT_*` variables do not apply to it.

`MCP_PUBLIC_URL` is the URL clients use; the server reports it in its [OAuth metadata](configuration.md#oauth-sign-in),
and Claude Code checks that it matches the URL it connected to.

### Sign in from Claude Code

Each user adds the server with the public client of the issuer realm and the callback port of its redirect URI:

```bash
claude mcp add --transport http --client-id keycloak-mcp --callback-port 33418 \
  keycloak https://mcp.example.com/mcp
```

Then they sign in with `/mcp` inside a session, or from the shell:

```bash
claude mcp login keycloak
```

The browser opens the Keycloak login page of the issuer realm. Claude Code stores the token and refreshes it. A user
without the `mcp-admin` role signs in but gets "needs additional permissions". A user with `mcp-admin` but no admin
roles gets `Keycloak returned 403 Forbidden` from each tool.

### Without a browser

`claude -p`, the Agent SDK and CI have no browser sign-in. For those, Claude Code can run
[`docker/mcp-token.sh`](../docker/mcp-token.sh) as a `headersHelper`: it runs the script on every connection and after
a `401`, and the script gets a fresh token with the password grant. The script needs `curl`.

The password grant needs Direct access grants on the `keycloak-mcp` client, and the machine holds a user's password.
Use a dedicated user with the `mcp-admin` role and only the admin roles the job needs, and turn the grant on only if
you need this setup.

```bash
claude mcp add-json keycloak \
  '{"type":"http","url":"https://mcp.example.com/mcp","headersHelper":"/path/to/mcp-token.sh"}'
```

Set these variables in the environment Claude Code runs in, for example as CI secrets:

```bash
MCP_ISSUER_URL=https://keycloak.example.com/realms/master
MCP_USER=<user>
MCP_PASS=<password>
```

## Check the connection

Run `claude mcp list`, or `/mcp` inside a session. Then ask, for example, "Which realms exist?"
