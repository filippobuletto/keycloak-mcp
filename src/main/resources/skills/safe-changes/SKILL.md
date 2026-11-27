---
name: safe-changes
description: Steps for changing Keycloak through this server - read the current state first, state the change, let the server's confirmations ask, and read the result back. Use before any create, update, delete, bind, logout or clear call on a Keycloak realm.
license: EUPL-1.2
---

# Safe changes in Keycloak

Follow these steps for every call that changes a realm. A read-only call
(any tool whose name starts with `get`, `list` or `search`) needs none of
them.

## 1. Read before you write

Read the object you are about to change with the matching read tool, for
example `getClient` before `updateClient`, `getUserRoles` before
`assignRealmRole`, or `getAuthenticationFlow` before any flow change. Keep what you read: it is the way back.

When the request names something that does not exist, or matches more than
one object (two users found by `searchUsers`, for example), stop and ask the
operator which one they mean.

## 2. State the change

Before the call, tell the operator in one or two sentences:

- which realm and which object the call changes,
- what the value is now and what it will be,
- who or what else it affects (users, clients, sign-ins), and
- how to undo it, or that it cannot be undone.

When the operator asked for exactly this change, go on. When the change is
your own idea, or it goes further than the request (more objects, a
realm-wide setting, a delete), wait for the operator to agree.

## 3. Let the server ask

Some tools ask the operator to confirm before they change anything. The
list, with what each removes or resets, is in
[references/confirmations.md](references/confirmations.md).

- Never set the confirm argument yourself. The server asks the operator; if
  the server runs in flag mode, the operator must say in the conversation
  that you may set it for this one call.
- When the operator declines, or the server refuses a call, report it and
  stop. Do not retry the call in another form, split it into smaller calls,
  or reach the same result through other tools.
- Writes to the `master` realm are refused by design. Do not look for a way
  around that; tell the operator to make the change in the admin console if
  they need it.

## 4. Prefer a throwaway realm

To try something out (a new flow, a mapper, a client setting), suggest
creating a test realm with `createRealm`, working there, and deleting it with
`deleteRealm` at the end. Change a realm that people sign in to only when the
operator asks for that realm.

## 5. Read back and report

After the call, read the object again with the same read tool and report
what changed, compared with what you read in step 1. When the result differs
from what you stated in step 2, say so plainly.

## Secrets

`getClientSecret` and `regenerateClientSecret` return a live credential.
Show it only when the operator asked for it, and do not repeat it later in
the conversation.
