# Changelog

All notable changes to keycloak-mcp are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-11-27

First release.

### Added

- 92 tools for the Keycloak Admin REST API in these families: realms, users, clients, client secrets, client scopes,
  client scope mappers, identity providers, identity provider mappers, authentication flows, required actions,
  sessions, events, roles and groups. See [Tools](https://github.com/filippobuletto/keycloak-mcp/blob/main/docs/tools.md).
- Write tools refuse the `master` realm. Destructive tools and realm-level changes ask for confirmation through an MCP
  elicitation, or, with `MCP_CONFIRM_MODE=flag` for clients without elicitation, need a `confirm=true` argument.
- Client secret tools, present only with `MCP_CLIENT_SECRETS_ENABLED=true`.
- Two read-only MCP resources, `keycloak://realms` and `keycloak://realms/{realm}`.
- Three MCP prompts: `audit-realm`, `troubleshoot-login` and `setup-client`.
- Four Agent Skills through the MCP Skills extension: `safe-changes`, `login-troubleshooting`, `flow-change` and
  `security-audit`.
- Two transports in one build: stdio, with an admin identity (password or client credentials grant), and Streamable
  HTTP, which accepts bearer tokens with the `mcp-admin` role, exchanges each user's token for an Admin API token and
  publishes OAuth protected resource metadata for browser sign-in. CORS for browser clients is configurable.
- Container images on GHCR for `linux/amd64` and `linux/arm64`, both distroless and running as user 65532:
  `ghcr.io/filippobuletto/keycloak-mcp:0.1.0` (Java runtime made with `jlink`) and `:0.1.0-native` (native build,
  also tagged `latest`).
- Release assets: the JVM application `keycloak-mcp-0.1.0.zip` (Java 25), the native binaries
  `keycloak-mcp-0.1.0-linux-amd64` and `keycloak-mcp-0.1.0-linux-arm64`, and `SHA256SUMS`.

[0.1.0]: https://github.com/filippobuletto/keycloak-mcp/releases/tag/v0.1.0
