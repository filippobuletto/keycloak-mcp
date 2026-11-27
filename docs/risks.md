# Before you use it

> [!WARNING]
> The write tools change a live Keycloak server, and the MCP client chooses which tool to call and with which
> arguments. Try the server against a test Keycloak first, and use it on a real server only after you have seen what it
> does there.

The risks to know about:

- **Changes take effect at once.** Creating, updating, or deleting users, clients, client scopes, mappers, identity
  providers, roles, and groups changes what people can sign in to and which tokens they get. A wrong change can lock
  users out or grant access to the wrong people. Deletions cannot be undone. An identity provider mapper with sync mode
  `FORCE` rewrites the data it maps for each user on every sign-in through that provider. Binding an authentication
  flow changes every sign-in it covers, and a broken flow can stop all users of a realm from signing in; keep an
  admin session open and bind the built-in flow back if that happens. Disabling a required action such as
  `CONFIGURE_TOTP` while a flow in use requires its step stops users without that credential from signing in.
  `logoutRealm` signs out every user and app of a realm at once, and Keycloak rejects every token issued before then.
  `clearEvents` deletes a realm's stored login or admin events, the audit trail of who signed in and who changed
  what; turning event recording off loses the events from then on.
- **The model can get it wrong.** An AI client may misread a request, pick the wrong realm or object, or call a tool
  you did not expect. The confirmation dialog and the `master` realm guard reduce this risk but do not remove it.
  The dialog names the action, the target and the realm; read it before you accept. A client can answer dialogs
  without asking you, for example through a Claude Code `Elicitation` hook. With `MCP_CONFIRM_MODE=flag` the client
  itself sets `confirm=true`, so the model can confirm a destructive call on its own. Review each call before you
  approve it.
- **The server can do whatever the Keycloak identity it acts as can do.** Over stdio that is its admin account: give it
  only the roles it needs, and only in the realms it must manage. Over HTTP it acts as each signed-in user, so every
  user with the `mcp-admin` role can do through the MCP client whatever their own Keycloak admin roles allow; grant
  `mcp-admin` only to users whose admin roles you would let an AI client use.
- **Data ends up in the client.** Users, attributes, events (with IP addresses, usernames and, when asked for, the JSON
  of admin changes), who holds each role, service account roles, the config of client and scope mappers (including any
  hardcoded claim values), realm settings attached as [resources](resources.md), and, when enabled,
  client secrets reach the MCP client and its conversation history.
- **Prompts are instructions, not scripts.** A [prompt](prompts.md) tells the model which tools to
  use, but the model still decides what to call. `setup-client` creates a client and changes its scopes after asking
  you; the read-only prompts tell the model to change nothing, which it can ignore.
- **Skills are guidance too.** A [skill](skills.md) describes steps the model should follow, such as reading
  before writing or auditing without changes; the model can still skip a step or call another tool. `security-audit`
  covers only the settings its checks read, some of which this server cannot show yet. A report without findings is not
  a complete security review.

On a real server, export the realm or back up the database before a session of changes, and keep the HTTP transport
behind TLS.
