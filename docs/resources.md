# Resources

Besides [tools](tools.md), the server offers read-only realm data as MCP resources. Reading a resource never writes
to Keycloak. Both resources return JSON in the same shape as the matching tool.

| URI | Content |
|---|---|
| `keycloak://realms` | Every realm: id, name, display name, enabled flag (as `listRealms`) |
| `keycloak://realms/{realm}` | One realm's settings (as `getRealm`), with the password policy and brute-force protection |

[Realms](tools.md#realms) under Tools describes the realm settings, the password policy and the `bruteForce` and
`tokens` blocks.

In Claude Code, type `@` and pick a resource, or type its URI with the server name, for example
`@keycloak:keycloak://realms/acme`. The server completes the realm name in the template for clients that ask. An
unknown realm fails with "Realm X not found".
