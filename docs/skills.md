# Skills

The server offers four [Agent Skills](https://agentskills.io) through the MCP Skills extension
(`io.modelcontextprotocol/skills`, protocol revision 2026-07-28). A skill is a set of Markdown instructions that the
client loads only when a job needs it, so it uses no context until then. The skills are part of the build: serving them
makes no Keycloak call, and they change only with a new release.

| Skill | What the model does |
|---|---|
| `safe-changes` | Before a write: reads the current state, states the change and the way back, lets the confirmation dialog ask, and reads the result back. Lists the tools that ask and what each removes |
| `login-troubleshooting` | Works out why one user cannot sign in: the user, the recorded login errors, lockout settings, sessions, flows and the client, with a table of Keycloak event errors and their usual causes. Changes nothing without asking |
| `flow-change` | Changes an authentication flow on a copy, reviews it with you, and binds it only after you agree, with the binding to restore stated. Has recipes for OTP by role, an identity provider redirect and restoring a binding |
| `security-audit` | Audits one realm against the checks of [kcwarden](https://github.com/iteratec/kcwarden) (iteratec, Apache-2.0): realm settings, clients, identity providers, client scopes, and an allowlist of who may hold sensitive roles that you give at the start. Reports findings by severity with the kcwarden check name. Changes nothing |

`security-audit` restates the auditors and monitors of kcwarden release v0.19.0, plus two event expiry checks added on
kcwarden's main branch after it, in this project's own words; it contains no kcwarden code. Every check reads its
setting through a read tool. When a call fails, for example because the signed-in user or the server's admin identity
lacks a permission, the skill
lists the check under "checks that could not run" with the error.

In Claude Code, skills from a server show up as slash commands named `/<server>:<skill>`, and the model may also load
one on its own when a request matches the skill's description:

```text
/keycloak:security-audit acme
/keycloak:login-troubleshooting jdoe in acme
```

Each file of a skill is also an MCP resource at `skill://keycloak/<skill>/...` with MIME type `text/markdown`. Clients
on protocol revisions before 2026-07-28 do not list skills and see these files only as resources; the three
[prompts](prompts.md) stay for those clients.
