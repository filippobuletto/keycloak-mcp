# Prompts

The server offers three MCP prompts for common jobs. Each prompt fills its arguments into fixed instructions; getting
a prompt makes no Keycloak call. Every argument is required and is one word. The server completes the `realm` argument
to realm names for clients that ask; Claude Code does not ask, so type the realm in full. For longer jobs the server
also offers [skills](skills.md).

| Prompt | Arguments | What the model does |
|---|---|---|
| `audit-realm` | `realm` | Reviews the realm's security settings, password policy and brute-force protection included, and lists findings by severity with the fix. Changes nothing |
| `troubleshoot-login` | `realm`, `username` | Reads the user, their sessions, recent sign-in errors and the realm's lockout and flows, and reports the likely cause. Changes nothing without asking |
| `setup-client` | `realm`, `clientId` | Asks for the client type, redirect URIs and web origins, then creates the OpenID Connect client and adds the scopes and mappers you ask for |

In Claude Code, prompts are slash commands named after the server:

```text
/mcp__keycloak__audit-realm acme
/mcp__keycloak__troubleshoot-login acme jdoe
/mcp__keycloak__setup-client acme portal
```

`troubleshoot-login` finds sign-in errors only when the realm records login events; see [Events](tools.md#events).
