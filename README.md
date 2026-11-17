# Shortline

A system-design-focused URL shortener built with Java and Spring Boot. The project demonstrates a read-optimized redirect path, owner-scoped link management, resilient analytics, and measurable performance trade-offs.

## Current scope

- Create short links with generated seven-character codes.
- Redirect short codes to validated HTTP(S) destinations.
- Return a clear `400 Bad Request` for invalid destinations and `404 Not Found` for unknown codes.
- Persist mappings in PostgreSQL and cache active redirects in Redis with expiry-aware TTLs.
- Create custom codes, set optional expiration, and deactivate links.
- Publish clicks to Kafka asynchronously and expose eventually consistent click counts.
- Expose health and Prometheus metrics endpoints.

## Run locally

Requirements: Java 21+, Maven, Docker Compose. Postgres is mapped to host port `5433` to avoid common local Postgres conflicts.

```bash
docker compose up -d postgres redis kafka
mvn spring-boot:run
```

Register an account, then create a link with HTTP Basic authentication:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo_user","password":"replace-this-with-a-long-password"}'

curl -u demo_user:replace-this-with-a-long-password \
  -X POST http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/engineering","customCode":"engineering"}'
```

Follow a short link at `http://localhost:8080/{code}`. Redirects are public; link creation, analytics, and deactivation require authentication. Analytics and deactivation are owner-scoped. Redirects return `302 Found` so browsers and intermediary caches do not retain a permanent destination if a link is later disabled.

## System design notes

The expected workload is read-heavy: redirects should remain fast even when analytics or other noncritical components are slow. PostgreSQL is the source of truth, Redis caches hot mappings, and Kafka carries analytics asynchronously. User passwords are BCrypt-hashed; creation, deactivation, and analytics use HTTP Basic authentication and enforce link ownership. The local benchmark is a baseline, not a production capacity claim.

See [docs/system-design.md](docs/system-design.md) for assumptions and architecture trade-offs, and [ADR 0001](docs/adr/0001-click-analytics-async.md) for the analytics delivery decision. Run `mvn test` for automated service tests, `python3 scripts/smoke_walkthrough.py` for the local end-to-end walkthrough, and `python3 scripts/benchmark_redirects.py <short-url>` for a redirect benchmark. See [local performance results](docs/performance.md).

This is a portfolio/demo service, not a hardened public service. Registration has no throttling or recovery flow; use HTTPS in production. If Redis is unavailable, redirects fall back to PostgreSQL after a five-second process-local circuit cooldown; cached entries have a five-minute maximum TTL. Click analytics are eventually consistent. A bounded background publisher queue can drop analytics events under overload or process shutdown without affecting redirects.

## API

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Create an account; responds `201 Created` |
| `POST` | `/api/v1/links` | Create an authenticated user's short link; responds `201 Created` |
| `GET` | `/{code}` | Redirect with `302 Found`, `404 Not Found`, or `410 Gone` for disabled/expired links |
| `DELETE` | `/api/v1/links/{code}` | Deactivate a link; responds `204 No Content` or `404 Not Found` |
| `GET` | `/api/v1/links/{code}/analytics` | Return the asynchronously aggregated click count |
| `GET` | `/actuator/health` | Liveness/readiness health |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape endpoint |

Register with `POST /api/v1/auth/register`, then send Basic credentials for link management. The creation endpoint accepts `{"url":"https://example.com/path","customCode":"docs","expiresAt":"2027-01-01T00:00:00Z"}`. Codes are seven-character Base62 strings unless a custom code (3–32 letters, numbers, hyphens, or underscores) is supplied. Only absolute HTTP and HTTPS destinations are accepted. Other users see `404 Not Found` when they request private analytics or deactivate a link they do not own.
