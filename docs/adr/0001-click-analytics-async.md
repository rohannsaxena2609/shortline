# ADR 0001: Keep click analytics off the redirect path

## Status

Accepted

## Context

Redirects are latency-sensitive. Persisting analytics synchronously would make a redirect depend on analytics storage availability and write latency.

## Decision

The redirect handler submits a small click message to a bounded local executor. The worker publishes it to Kafka, and a consumer persists events in PostgreSQL keyed by a generated event ID. Replayed Kafka messages are ignored when the ID already exists. The executor has a fixed queue limit; overload drops analytics work and records a metric rather than slowing redirects. Kafka producer idempotence and acknowledgements are enabled.

## Consequences

Click counts are eventually consistent. Some click events can be lost if the process stops before its local queue is drained or when that queue is full; redirects still succeed. A future durable local outbox can remove the process-loss window, at the cost of writing an event transactionally to PostgreSQL on the redirect path.
