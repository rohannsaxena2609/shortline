# Shortline

A system-design-focused URL shortener built with Java and Spring Boot. The project starts as a clean, runnable service and grows toward a read-optimized redirect path, resilient analytics, and measurable performance.

## Current scope

- Create short links with generated or custom codes.
- Redirect short codes to validated HTTP(S) destinations.
- Persist mappings in PostgreSQL.
- Keep the codebase modular so Redis caching and asynchronous click analytics can be added as separate steps.

## Run locally

Requirements: Java 21+, Maven, Docker Compose.

```bash
docker compose up -d postgres
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

## API

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/api/v1/links` | Create a short link; responds `201 Created` |
| `GET` | `/{code}` | Redirect with `302 Found`, or `404 Not Found` |

The creation endpoint accepts `{"url":"https://example.com/path"}`. Codes are seven-character Base62 strings. Only absolute HTTP and HTTPS destinations are accepted.
