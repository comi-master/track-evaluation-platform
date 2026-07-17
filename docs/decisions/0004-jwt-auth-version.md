# ADR 0004: Single access token with database auth version

## Status

Accepted for milestone 2.

## Context

Milestone 2 needs stateless API authentication, immediate account-disable enforcement and a real logout behavior without introducing Redis sessions, token tables or Refresh Tokens before they are justified.

## Decision

Issue one signed JWT Access Token containing `sub`, `username`, `authVersion`, `jti`, `iat`, `exp` and `iss`. Every protected request verifies signature, issuer and expiry, then reloads `sys_user` and requires an active, non-deleted user whose `auth_version` matches the claim. Logout increments `auth_version` in MySQL.

The signing key is supplied as a Base64 environment secret with at least 256 bits of decoded entropy. Token TTL and issuer are configurable. Passwords use BCrypt and no credential, hash, JWT secret, full token or Authorization header is logged.

## Consequences

- Logout immediately invalidates all tokens previously issued to that user, across every device.
- Each authenticated request performs one user lookup; this is an intentional clarity and revocation tradeoff for the current modular monolith.
- There is no Refresh Token, per-device logout, Redis session state, token blacklist, RBAC or OAuth2 in this milestone.
- A future change to per-device sessions requires a new decision and migration rather than silently changing this contract.
