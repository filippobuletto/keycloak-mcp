---
name: security-audit
description: Read-only security audit of one Keycloak realm, following the checks of kcwarden - realm settings, clients, identity providers, client scopes, and who may hold sensitive roles. Use when asked to audit, review or harden a realm's configuration.
license: EUPL-1.2
---

# Security audit of a realm

Audit one realm per run against the checks of
[kcwarden](https://github.com/iteratec/kcwarden) (iteratec, Apache-2.0),
restated here for this server's read tools. The checks follow kcwarden
release v0.19.0, plus two event checks added on kcwarden's main branch
after it, labelled as such. The `master` realm can be audited too.

This audit only reads. Do not change anything in Keycloak during the run,
even when a fix looks obvious. When the operator asks for fixes afterwards,
make them one at a time following the `safe-changes` skill.

## 1. Agree on the scope

Ask the operator:

1. Which realm, if not given.
2. Which roles are sensitive, and which groups, clients or scopes may hold
   each one. This feeds the role guardrails. The operator may skip it; then
   say in the report that the guardrails did not run.
3. Whether to change a default limit for this run (for example a longer
   access token lifespan they accept). Use the operator's limit and say so
   in the report next to each finding it affects.

## 2. Read the realm once

Call `getRealm`, `getEventSettings` and `getServerVersion`, and list the
realm's objects with `listClients`, `listIdentityProviders`,
`listClientScopes`, `listRealmRoles` and `listGroups`.

When the realm has more than 50 clients, tell the operator how many there
are and ask whether to check all of them, only those whose clientId matches
a filter, or a sample. Report which clients were covered.

## 3. Run the checks by area

Load the reference file of each area and follow it check by check:

| Area | Reference file |
|---|---|
| Realm settings and events | [references/realm.md](references/realm.md) |
| Clients | [references/clients.md](references/clients.md) |
| Identity providers | [references/identity-providers.md](references/identity-providers.md) |
| Client scopes | [references/client-scopes.md](references/client-scopes.md) |
| Sensitive roles, scopes and mappers | [references/role-guardrails.md](references/role-guardrails.md) |

Each check names the setting to read, when it is a finding, the default
severity, why it matters and the recommended setting. Role holders,
composite roles and service accounts are read with `getRealmRole` and
`getServiceAccount`. When a tool call a check needs fails, for example
because the signed-in user or the server's admin identity lacks a
permission, skip the check and list it at the end with the error.

## 4. Report

Write the report in the conversation:

1. A summary line: realm, date, areas covered, number of findings by
   severity.
2. Findings ordered from Critical to Info. For each: severity, the kcwarden
   check name, the object (realm, client, provider, scope, group), the
   setting and its current value, the risk in one or two sentences, and the
   recommended value. Mark findings that used an operator's limit.
3. Checks that could not run, each with the reason and where the operator
   can look.
4. What was left out on purpose (skipped areas, clients outside the filter).

Then offer to save the report as a Markdown file. Write the file with the
host's own file tools, and only when the operator says yes.

The audit covers the settings these checks read. It is not a complete
security review, and a realm without findings can still have problems the
checks do not look for; say so in the report.
