# Client scope checks

List the scopes with `listClientScopes` and read each with
`getClientScope`, which shows its protocol mappers with their type and
config. The check carries the kcwarden auditor name.

## UsingNonDefaultUserAttributesInScopesWithoutUserProfilesFeatureIsDangerous

- **Read:** each scope's mappers of type oidc-usermodel-attribute-mapper and
  their user.attribute config key, from `getClientScope`, and
  unmanagedAttributePolicy from `getRealm`.
- **Finding when:** such a mapper puts an attribute other than the standard
  ones (firstName, lastName, middleName, nickname, username, email,
  emailVerified, phoneNumber, phoneNumberVerified, mobile_number, picture,
  profile, website, gender, birthdate, zoneinfo, locale, updatedAt) into a
  token, and users may edit unmanaged attributes: unmanagedAttributePolicy
  is ENABLED. ADMIN_EDIT, ADMIN_VIEW and null (unmanaged attributes off)
  mean users cannot edit them.
- **Severity:** High.
- **Why it matters:** a user can then change the attribute in the account
  console, for example a customer number, and apps that trust the claim act
  on the changed value. Attributes imported read-only from LDAP can give a
  false finding.
- **Recommended:** declare the attribute in the user profile with view or
  edit rights for administrators only, or set the unmanaged attribute policy
  to disabled or admin-only.
