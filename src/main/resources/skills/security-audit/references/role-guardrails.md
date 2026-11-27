# Role guardrails

kcwarden calls these checks monitors: they compare the realm against an
allowlist only the operator knows. Ask for it at the start of the run, one
entry per thing to guard, for example:

- the role finance-admin (realm role) may be held only by the group
  /finance/admins;
- the scope billing:write may be used only by the clients billing-api and
  billing-ui;
- mappers of type oidc-usermodel-attribute-mapper that put the attribute
  customer_id into tokens may appear only in the scope customer.

Names in an allowlist may be exact or patterns; ask which when unclear. Each
entry may carry its own severity (Info, Low, Medium, High, Critical); the
default is Medium. Report the operator's note for an entry, if they gave one,
with each finding. When the operator gives no allowlist, skip this file and
say so in the report.

Tip from kcwarden: run an entry with an empty allowlist first, to see that
it finds the holders you expect, then add the allowed ones.

Each check carries the kcwarden monitor name.

## ClientWithSensitiveRole

- **Read:** each client's fullScopeAllowed, scopeMappings and
  protocolMappers with `getClient`, and the roles of its default and
  optional scopes with `getClientClientScopes` and `getClientScope`. Expand
  composite roles with `getRealmRole`. With full scope allowed, the client
  can carry every role its users hold: find the role's holders with
  `getRealmRole` (users and groups).
- **Allowlist entry:** a role (realm role, or client role with its
  clientId) and the clients allowed to receive it in their tokens.
- **Finding when:** a client outside the list can carry the role, directly
  or through a composite role, in its tokens.
- **Why it matters:** an app that receives a role it should not have can act
  with that role's rights.

## ClientWithSensitiveScope

- **Read:** the clients with `listClients`, and each client's default and
  optional scopes with `getClientClientScopes`.
- **Allowlist entry:** a scope name and the clients allowed to use it.
- **Finding when:** a client outside the list has the scope as default or
  optional.
- **Why it matters:** a scope can carry roles and claims into tokens; a
  client that should not have them can then use them.

## GroupWithSensitiveRole

- **Read:** the groups with `listGroups`, and each group's realm and client
  roles with `getGroup`. To match client roles, `listClientRoles` shows the
  roles of a client; `listRealmRoles` shows which realm roles are
  composite.
- **Allowlist entry:** a role (realm role, or client role with its
  clientId) and the group paths allowed to hold it.
- **Finding when:** a group outside the list has the role.
- **Why it matters:** every member of the group gets the role, including
  members added later.
  A group that holds a composite role gets the roles it contains; read
  them with `getRealmRole`.

## ProtocolMapperWithConfig

- **Read:** each client's own mappers (protocolMappers) with `getClient`,
  its default and optional scopes with `getClientClientScopes`, and each
  scope's mappers with type and config with `getClientScope`.
- **Allowlist entry:** a mapper type, the config values to match (for
  example user.attribute customer_id), and the clients allowed to use it.
- **Finding when:** a client outside the list has a matching mapper of its
  own, or gets one through one of its default or optional scopes.
- **Why it matters:** the mapper puts data into tokens; a client outside the
  list then sees data it should not.

## ProtocolMapperWithConfigOnClientScope

- **Read:** the scopes with `listClientScopes`, and each scope's mappers with
  type and config with `getClientScope`.
- **Allowlist entry:** a mapper type, the config values to match, and the
  scopes allowed to hold such a mapper.
- **Finding when:** a scope outside the list holds a matching mapper. List
  the clients that use the scope (from `getClientClientScopes`) with the
  finding.
- **Why it matters:** every client that uses the scope then receives the
  mapped data.

## RoleWithSensitiveAssociatedRole

- **Read:** the composite realm roles from `listRealmRoles`, and each
  composite's roles with `getRealmRole` (composites; client roles carry
  their clientId). Follow nested composites with `getRealmRole` again, and
  client composites with its clientId.
- **Allowlist entry:** a role and the composite roles allowed to contain it.
- **Finding when:** a composite role outside the list contains the role,
  directly or through other composites.
- **Why it matters:** whoever holds the composite role gets the sensitive
  role too, often without anyone noticing.

## ServiceAccountWithGroup

- **Read:** the clients with a service account (serviceAccountsEnabled
  from `listClients`), and each one's groups with `getServiceAccount`.
- **Allowlist entry:** a group path, the service accounts allowed in it, and
  whether a service account without any group is acceptable.
- **Finding when:** a service account outside the list is in the group, or,
  when that is not acceptable, a service account is in no group.
- **Why it matters:** the service account gets every role the group grants.

## ServiceAccountWithSensitiveRole

- **Read:** each service account with `getServiceAccount`: its direct
  realm and client roles, its effective realm roles (through groups and
  composites) and its groups. For a client role, also check the client
  roles of its groups with `getGroup`, and expand composites with
  `getRealmRole`.
- **Allowlist entry:** a role and the service accounts allowed to hold it.
- **Finding when:** a service account outside the list holds the role,
  directly, through a group, or through a composite role.
- **Why it matters:** a service account acts without a person signing in;
  whoever holds the client's secret holds the role.
