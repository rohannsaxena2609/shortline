#!/usr/bin/env python3
"""Measure public redirect latency without following the 302 response."""
import argparse
import concurrent.futures
import http.client
import json
import platform
import threading
import time
from collections import Counter
from urllib.parse import urlsplit

thread_local = threading.local()

def percentile(samples, percentile_value):
    ordered = sorted(samples)
    index = max(0, min(len(ordered) - 1, int((percentile_value / 100) * len(ordered) + 0.999999) - 1))
    return ordered[index]

def request_once(target):
    parsed = urlsplit(target)
    connection = getattr(thread_local, "connection", None)
    host = (parsed.hostname, parsed.port, parsed.scheme)
    if connection is None or getattr(thread_local, "host", None) != host:
        connection = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=10)
        thread_local.connection = connection
        thread_local.host = host
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query
    start = time.perf_counter()
    try:
        connection.request("GET", path, headers={"Connection": "keep-alive"})
        response = connection.getresponse()
        response.read()
        status = response.status
    except Exception:
        connection.close()
        thread_local.connection = None
        status = 0
    return (time.perf_counter() - start) * 1000, status

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("short_url", help="A public Shortline redirect URL, e.g. http://localhost:8080/demo")
    parser.add_argument("--requests", type=int, default=1000)
    parser.add_argument("--concurrency", type=int, default=32)
    parser.add_argument("--mode", choices=("redis-cached", "db-fallback"), default="redis-cached", help="Label for the backing-path setup")
    parser.add_argument("--output", help="Optional JSON output path")
    args = parser.parse_args()
    if args.requests < 1 or args.concurrency < 1:
        parser.error("--requests and --concurrency must be positive")
    # Warm the Redis cache before timing the measured set.
    request_once(args.short_url)
    wall_start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(request_once, [args.short_url] * args.requests))
    elapsed = time.perf_counter() - wall_start
    latencies = [latency for latency, _ in results]
    statuses = Counter(str(status) for _, status in results)
    report = {
        "target": args.short_url,
        "requests": args.requests,
        "concurrency": args.concurrency,
        "elapsed_seconds": round(elapsed, 3),
        "throughput_requests_per_second": round(args.requests / elapsed, 2),
        "latency_ms": {
            "p50": round(percentile(latencies, 50), 2),
            "p95": round(percentile(latencies, 95), 2),
            "p99": round(percentile(latencies, 99), 2),
            "max": round(max(latencies), 2),
        },
        "status_counts": dict(statuses),
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "mode": f"single-process local demo; keep-alive; {args.mode}; 1 warm-up request",
        },
    }
    rendered = json.dumps(report, indent=2) + "\n"
    print(rendered, end="")
    if args.output:
        with open(args.output, "w", encoding="utf-8") as output:
            output.write(rendered)
    if set(statuses) != {"302"}:
        raise SystemExit("Benchmark received non-302 responses; inspect the target/link state.")

if __name__ == "__main__":
    main()
