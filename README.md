# Shortline

A system-design-focused URL shortener built with Java and Spring Boot. The project starts as a clean, runnable service and grows toward a read-optimized redirect path, resilient analytics, and measurable performance.

## Current scope

- Create short links with generated seven-character codes.
- Redirect short codes to validated HTTP(S) destinations.
- Return a clear `400 Bad Request` for invalid destinations and `404 Not Found` for unknown codes.
- Persist mappings in PostgreSQL and cache active redirects in Redis with expiry-aware TTLs.
- Create custom codes, set optional expiration, and deactivate links.

## Run locally

Requirements: Java 21+, Maven, Docker Compose.

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

Create a link:

```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/engineering"}'
```

Follow a short link at `http://localhost:8080/{code}`. Redirects return `302 Found` so browsers and intermediary caches do not retain a permanent destination if a link is later disabled.

## System design notes

The expected workload is read-heavy: redirects should remain fast even when analytics or other noncritical components are slow. The initial version uses PostgreSQL as the source of truth. Planned steps add Redis for hot mappings and a queue-backed analytics pipeline; both will be introduced with explicit failure behavior and measurements.

See [docs/system-design.md](docs/system-design.md) for assumptions, architecture evolution, and design trade-offs.

Authentication and click analytics are planned follow-up milestones; they are not implemented yet. If Redis is unavailable, redirects fall back to PostgreSQL; cached entries have a five-minute maximum TTL.

## API

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/v1/links` | Create a short link; responds `201 Created` |
| `GET` | `/{code}` | Redirect with `302 Found`, `404 Not Found`, or `410 Gone` for disabled/expired links |
| `DELETE` | `/api/v1/links/{code}` | Deactivate a link; responds `204 No Content` or `404 Not Found` |

The creation endpoint accepts `{"url":"https://example.com/path","customCode":"docs","expiresAt":"2027-01-01T00:00:00Z"}`. Codes are seven-character Base62 strings unless a custom code (3–32 letters, numbers, hyphens, or underscores) is supplied. Only absolute HTTP and HTTPS destinations are accepted. Links currently have no authentication or ownership model, so treat this MVP as a local/demo service.
