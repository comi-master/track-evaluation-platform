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

Only ping and Actuator exist now. Authentication, datasets, files, tasks, points, results, intervals, and reports listed in the product brief are planned for later milestones and must not be treated as available APIs.
