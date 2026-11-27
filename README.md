# keycloak-mcp

An MCP server that exposes the Keycloak Admin REST API as tools, so an MCP client such as Claude Code can inspect and
administer a Keycloak instance in plain language.

The server ships as one artifact with two transports:

- **stdio** runs the server as a local process of the client. The Keycloak credentials in its environment are the
  trust boundary.
- **Streamable HTTP** serves `/mcp` to many clients. Every request needs a bearer token with the `mcp-admin` role.

Destructive tools run only after you accept a confirmation dialog that the server sends through the MCP client (an
MCP elicitation), and every write tool refuses the `master` realm. Tools that return client secrets exist only when
`MCP_CLIENT_SECRETS_ENABLED=true`.

> [!WARNING]
> The write tools change a live Keycloak server, and the MCP client chooses which tool to call and with which
> arguments. Try the server against a test Keycloak first, and use it on a real server only after you have seen what it
> does there. Read [Before you use it](docs/risks.md) for the risks: changes take effect at once, the model can get it
> wrong, the server can do whatever its Keycloak account can do, and data ends up in the client.

## Install

Each release publishes the container images `ghcr.io/filippobuletto/keycloak-mcp:<version>` (Java runtime) and
`:<version>-native` (native build, also `latest`) for `linux/amd64` and `linux/arm64`, and on the
[releases page](https://github.com/filippobuletto/keycloak-mcp/releases) the JVM application for Java 25 and native
Linux binaries. [Connect Claude Code](docs/connect.md) shows how to run them against your Keycloak. The rest of this
page builds the server from source.

## Requirements

- Java 25 (`.sdkmanrc` pins it)
- Docker, for the local Keycloak, the tests (Dev Services) and native builds (no local GraalVM needed)
- Keycloak 26 as the target; development uses `quay.io/keycloak/keycloak:26.8.2`

## Quick start

Start a local Keycloak with a seeded realm, then build the server:

```bash
docker compose -f docker/compose.yaml up -d
./mvnw package -DskipTests
```

The compose Keycloak listens on <http://localhost:8080> with admin `admin/admin`. It imports one realm, and
`docker/seed-master.sh` adds the HTTP sign-in objects to `master` (see [Development](docs/development.md#local-keycloak)):

| Realm | Purpose | Contents |
|---|---|---|
| `acme` | Sample target data | users `jdoe`, `asmith`, `bwhite`; groups `/finance`, `/finance/payables`, `/engineering`; realm roles `viewer`, `editor`; clients `acme-web`, `reports-service` |
| `master` | Token issuer for the HTTP transport, after `docker/seed-master.sh` | clients `keycloak-mcp` (public) and `keycloak-mcp-server` (token exchange); users `mcp-user` (role `mcp-admin`, admin of every realm), `mcp-viewer` (role `mcp-admin`, may only view users of `acme`), `mcp-guest` (no role); each password equals the username |

Add the server to Claude Code over stdio, from the repository root:

```bash
claude mcp add keycloak \
  -e KEYCLOAK_URL=http://localhost:8080 \
  -e KEYCLOAK_USERNAME=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -- java -Dquarkus.profile=stdio -jar "$PWD/target/quarkus-app/quarkus-run.jar"
```

Check the connection with `claude mcp list`, or with `/mcp` inside a session. Then ask, for example, "Which groups is
asmith in, in realm acme?"

## Documentation

| Document | Contents |
|---|---|
| [Before you use it](docs/risks.md) | The risks of letting an MCP client change a Keycloak server, and how to limit them |
| [Connect Claude Code](docs/connect.md) | Connect to your Keycloak: the images, the admin service account for stdio, a shared HTTP server that acts as each signed-in user, with browser sign-in or a token helper |
| [Configuration](docs/configuration.md) | Target Keycloak and grant types, HTTP authentication, CORS, OAuth sign-in, confirmations, client secrets |
| [Tools](docs/tools.md) | Every tool with an example prompt |
| [Resources](docs/resources.md) | The read-only realm data offered as MCP resources |
| [Prompts](docs/prompts.md) | The three prompts for auditing a realm, troubleshooting a sign-in and setting up a client |
| [Skills](docs/skills.md) | The four Agent Skills for longer jobs |
| [Development](docs/development.md) | Tests, dev mode, formatting, the native and distroless builds, connecting Claude Code to the local Keycloak, the coverage, Trivy and SonarQube checks, CI and releases |

## License

Copyright 2026 Filippo Buletto.

keycloak-mcp is licensed under the [European Union Public Licence v. 1.2](LICENSE) (EUPL-1.2). You may use it for
any purpose, personal or commercial. If you distribute a modified version, or let others use one over a network, you
must publish its source code under the EUPL-1.2 and keep the copyright and licence notices.

Contributions are welcome under the terms in [CONTRIBUTING.md](CONTRIBUTING.md), which require a Developer Certificate
of Origin sign-off on every commit.

### Non-commercial project

keycloak-mcp is a personal, non-commercial project. Its author supplies it free of charge and does not sell it, offer
paid support or paid features, or otherwise monetise it. This describes how the author supplies the project; it does
not restrict your use, which the licence governs.

The project comes with no commitment to support, maintenance, fixes, security updates, or releases. Issues and pull
requests may be answered, or not, at the maintainer's discretion. Nothing in this repository, its issues, or its
discussions is a promise about the software's fitness for any purpose.

### Disclaimer

This project is not affiliated with, endorsed by, or sponsored by the Keycloak project or the Cloud Native Computing
Foundation (CNCF). Keycloak is a CNCF project, licensed separately under the Apache License 2.0. "Keycloak" is a
trademark of its owners and is used here only to describe what this project works with.

The software is provided "as is", without warranty of any kind. To the fullest extent permitted by applicable law,
neither the original author nor any current owner or contributor of this project is liable for any damage, loss, or
misconfiguration caused by using it, including changes it makes to a Keycloak server. See Articles 7 and 8 of the
licence for the full terms.
