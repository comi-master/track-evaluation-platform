# API Contract

## Uniform envelope

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "requestId": "request-12345678",
  "timestamp": "2026-07-15T00:00:00Z"
}
```

The timestamp above is illustrative, not captured runtime output.

## Implemented endpoints

### `GET /api/v1/ping`

Returns HTTP 200 and a success envelope containing `status`, `application`, and a server timestamp. An incoming `X-Request-Id` matching `[A-Za-z0-9._-]{8,128}` is preserved; otherwise the server generates a UUID. The same ID is returned in the response header and body.

### Actuator

Configured endpoints are `/actuator/health`, `/actuator/info`, `/actuator/metrics`, and `/actuator/prometheus`. Health details are not exposed. Production exposure/network policy will be hardened when security and deployment profiles exist.

### Authentication

- `POST /api/v1/auth/register` creates a normalized lowercase user and returns public user fields only (201).
- `POST /api/v1/auth/login` returns a Bearer Access Token, expiry seconds, and public user fields (200).
- `GET /api/v1/auth/me` returns the authenticated user's public fields (200).
- `POST /api/v1/auth/logout` increments `sys_user.auth_version` (200). This invalidates every token previously issued to that user, not only one device.

Usernames are 3-64 lowercase-normalized ASCII letters, digits, dot, underscore, or hyphen. Passwords are 8-64 characters and are stored only as BCrypt hashes. Unknown user and wrong password use the same public 401 response.

### Datasets

- `POST /api/v1/datasets` creates an owned dataset (201).
- `GET /api/v1/datasets/{id}` reads an owned active dataset (200).
- `GET /api/v1/datasets?page=1&size=20&keyword=` returns an owner-scoped page ordered by `created_at DESC, id DESC` (200); size is capped at 100.
- `PUT /api/v1/datasets/{id}` updates name/description using the request `version` (200); stale versions return 409.
- `DELETE /api/v1/datasets/{id}` performs a logical delete (200).

All dataset SQL includes the current `user_id` and `deleted = 0`. Another user's resource and an absent resource both return 404.

### OpenAPI

`/v3/api-docs` and `/swagger-ui.html` are public. Protected operations declare the HTTP Bearer JWT scheme.

## Error mapping

| Business code | HTTP status | Meaning |
| --- | --- | --- |
| `INVALID_ARGUMENT` | 400 | Invalid input or unreadable body |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Authenticated but not permitted |
| `RESOURCE_NOT_FOUND` | 404 | Resource absent or not visible |
| `CONFLICT` | 409 | Uniqueness/current-state conflict |
| `FILE_FORMAT_ERROR` | 400 | Uploaded file violates format contract |
| `TASK_STATE_ERROR` | 409 | Illegal task transition |
| `INFRASTRUCTURE_ERROR` | 503 | Required dependency unavailable |
| `INTERNAL_ERROR` | 500 | Safe generic response; full cause stays in logs |

Ping, Actuator, authentication, datasets and OpenAPI are implemented. Files, tasks, points, analysis results, intervals and reports are planned for later milestones and must not be treated as available APIs. There is no Refresh Token, Redis login state or RBAC API.
