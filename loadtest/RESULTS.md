# Load test results

Measured with `loadtest/load.js` — a dependency-free Node script that issues a
fixed number of requests with bounded concurrency and reports throughput and
latency percentiles.

## Environment

| | |
|---|---|
| Host | Local machine, Windows on ARM (Snapdragon), 16 GB RAM |
| Runtime | JDK 25, Spring Boot 3.3.5 |
| Database | H2 in-memory |
| Endpoint | `GET /api/conversations` (session-authenticated, JSON list) |
| AI provider | Ollama (local), so no network variance from a hosted provider |

## Command

```bash
node loadtest/load.js http://localhost:8080/api/conversations 300 20 "JSESSIONID=..."
```

## Results

### 300 requests, concurrency 20

| Metric | Value |
|---|---|
| Duration | 2.07 s |
| Throughput | **144.7 req/s** |
| p50 | 117.7 ms |
| p95 | 266.1 ms |
| p99 | 322.7 ms |
| Max | 351.6 ms |
| Status | 200 = 300 ✅ |

### 500 requests, concurrency 50

| Metric | Value |
|---|---|
| Duration | 3.93 s |
| Throughput | **127.4 req/s** |
| p50 | 306.4 ms |
| p95 | 1,115.8 ms |
| p99 | 1,359.9 ms |
| Max | 1,444.9 ms |
| Status | 200 = 500 ✅ |

## Interpretation

- **Zero errors** at both concurrency levels.
- Throughput **plateaus at ~130 req/s** between 20 and 50 concurrent clients
  while p95 latency rises roughly 4×. That is the saturation point of a single
  instance — adding load past it buys latency, not throughput.
- These numbers describe the **application**, not the AI. Chat endpoints are
  dominated by model latency (hundreds of milliseconds to seconds), which is
  why the throughput test targets an endpoint that exercises the security
  filter, JPA query and JSON serialisation without any model call.
- A single Render free-tier instance is far below this ceiling; the rate limiter
  (20 requests/minute per user) is the binding constraint in production, not CPU.

## Reproducing

```bash
# 1. Start the app
./mvnw spring-boot:run

# 2. Log in and copy the JSESSIONID cookie from the browser dev tools

# 3. Run
node loadtest/load.js http://localhost:8080/api/conversations 300 20 "JSESSIONID=..."
```
