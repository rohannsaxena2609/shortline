# Local redirect benchmark

Measured on 2026-11-17 using the checked-in Python standard-library runner against a local Spring Boot process. Each run made 1,000 public GET requests at concurrency 32, kept connections alive, warmed the target once, and received 302 for every request. PostgreSQL, Redis, and Kafka ran in Docker Compose on the same machine.

| Backing path | Requests/sec | p50 | p95 | p99 | Max | 302 responses |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Redis cached | 2,639 | 8.42 ms | 25.31 ms | 34.01 ms | 94.26 ms | 1,000 / 1,000 |
| PostgreSQL fallback (Redis stopped) | 2,617 | 9.52 ms | 26.30 ms | 41.15 ms | 66.55 ms | 1,000 / 1,000 |

These are single-run local measurements, not statistically controlled or production-capacity results. At this load, the observed Redis improvement is small; repeat runs and deployment-like network conditions are needed before drawing a performance conclusion. The results are also checked in as [`local-benchmark.json`](results/local-benchmark.json) and [`db-fallback-benchmark.json`](results/db-fallback-benchmark.json).

Reproduce the run after starting the stack and app:

```bash
python3 scripts/benchmark_redirects.py http://127.0.0.1:8080/<active-code> \
  --requests 1000 --concurrency 32 --mode redis-cached
```

For a DB fallback run, stop Redis, run with `--mode db-fallback`, then start Redis again. The script warms once before timing and validates that every measured result is a 302. Do not compare runs taken with different request counts, concurrency, machine load, or application versions as if they were controlled experiments.
