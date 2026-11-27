# Tools that ask before they change anything

Each tool below asks the operator to confirm before it acts. All of them
except `createRealm` are refused for the `master` realm. Tell the operator
what the call removes or resets before you make it.

| Tool | What it removes or resets | Way back |
|---|---|---|
| `createRealm` | Nothing; adds a realm with Keycloak's default settings | `deleteRealm` |
| `deleteRealm` | The realm with all its users, clients, roles and groups | None |
| `deleteUser` | The user, their credentials and sessions | None; a new user gets a new id |
| `deleteClient` | The client; apps using it can no longer sign in | None; a new client gets a new secret |
| `deleteGroup` | The group and its subgroups; members lose the roles it granted | None |
| `deleteRealmRole` | The role, from every user and group that held it | None |
| `deleteClientScope` | The scope, from every client that had it; its claims leave their tokens | None |
| `deleteClientScopeMapper` | The mapper; its claim leaves the tokens of every client using the scope | Add it again with `addClientScopeMapper` |
| `setRealmDefaultClientScope` | Which scopes new clients get; existing clients keep theirs | Set it back |
| `deleteIdentityProvider` | The provider and the users' links to it; it leaves the login page | None |
| `updateIdentityProvider` | Asks when the change disables an enabled provider, which stops every sign-in through it | Enable it again |
| `deleteIdentityProviderMapper` | The mapper; data it already wrote to users stays | Add it again with `addIdentityProviderMapper` |
| `deleteAuthenticationFlow` | The flow with its sub-flows and configs | Rebuild it, or copy it first with `copyAuthenticationFlow` |
| `deleteFlowExecution` | A step or sub-flow, with its steps and configs | Add it again |
| `deleteExecutionConfig` | A step's config; the step runs with its defaults | Set it again with `setExecutionConfig` |
| `bindRealmFlow` | Which flow every sign-in of that kind runs, at once | Bind the alias it returns |
| `setClientFlowOverride` | Which flow one client's sign-ins run | Set or clear the override again |
| `updateRequiredAction` | Asks when disabling an action or changing a default action; users without the matching credential may be unable to sign in | Set it back |
| `updateEventSettings` | Asks when the change stops recording events or removes a listener | Turn it back on; events not recorded meanwhile are lost |
| `clearEvents` | Every stored event of one kind (login or admin) | None |
| `logoutUser` | All the user's sessions, including offline ones; older tokens stop working | None; the user signs in again |
| `logoutRealm` | Every session in the realm; every user and app must sign in again | None |
